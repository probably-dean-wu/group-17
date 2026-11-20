package org.accessgraph.data_access;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.accessgraph.entity.Connection;
import org.accessgraph.entity.ServiceAlert;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.TransitNetwork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gateway reads a copy of the real TTC subway feed, saved on November 20, 2026, from a loopback
 * server. No test contacts the TTC.
 */
class TtcAlertGatewayTest {
    private static final String ST_GEORGE = "43283";
    private static final String BAY = "14579";
    private static final String BLOOR_YONGE = "14572";
    private static final String SHERBOURNE = "14574";
    private static final String CASTLE_FRANK = "43301";
    private static final String BROADVIEW = "43302";

    private HttpServer server;
    private volatile int status = 200;
    private volatile String body;
    private final ConcurrentLinkedQueue<String> requests = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> userAgents = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream fixture = getClass().getResourceAsStream("/ttc-subway-alerts.txt")) {
            body = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/alerts/subway", this::serve);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readsTheNoServiceAlertsOfTheSavedTtcFeed() throws IOException {
        List<ServiceAlert> alerts = gateway().getNoServiceAlerts();

        assertEquals(2, alerts.size(), "The two advance notices (REDUCED_SERVICE) are left out.");
        ServiceAlert lineTwo = alerts.get(0);
        assertEquals("Line 2", lineTwo.getLine());
        assertEquals(List.of(ST_GEORGE, BAY, BLOOR_YONGE, SHERBOURNE, CASTLE_FRANK, BROADVIEW),
                List.copyOf(lineTwo.getStationIds()));
        assertEquals("Line 2 Bloor-Danforth: There is no subway service between St George and Broadview stations "
                + "due to for planned signal work. Shuttle buses are running.", lineTwo.getDescription());
        Instant start = Instant.ofEpochSecond(1795255200L);
        Instant end = Instant.ofEpochSecond(1795336200L);
        assertFalse(lineTwo.isActiveAt(start.minusSeconds(1)));
        assertTrue(lineTwo.isActiveAt(start));
        assertFalse(lineTwo.isActiveAt(end));

        ServiceAlert lineOne = alerts.get(1);
        assertEquals("Line 1", lineOne.getLine());
        assertEquals(List.of("15205", "15204", "11860", "43279"), List.copyOf(lineOne.getStationIds()),
                "Finch West, Downsview Park, Sheppard West and Wilson.");
        assertTrue(lineOne.isActiveAt(Instant.ofEpochSecond(1795237140L)));

        assertEquals(List.of("/alerts/subway?format=text"), List.copyOf(requests));
        assertTrue(userAgents.stream().allMatch(agent -> agent.startsWith("AccessGraph/")));
    }

    @Test
    void savedAlertClosesExactlyTheConnectionsOfTheStretchItNames() throws IOException {
        ServiceAlert lineTwo = gateway().getNoServiceAlerts().get(0);
        List<Station> stations = new BundledStationRepository().load();
        TransitNetwork network = new FileTransitNetworkDataAccess(stations).getNetwork();

        Set<String> closed = new TreeSet<>();
        for (Station station : network.getConnectedStations()) {
            for (Connection connection : network.connectionsFrom(station.getId())) {
                if (lineTwo.closes(connection)) {
                    closed.add(station.getName() + " > " + network.getStation(connection.getToId()).getName());
                }
            }
        }

        assertEquals(Set.of("St George > Bay", "Bay > St George", "Bay > Bloor-Yonge", "Bloor-Yonge > Bay",
                "Bloor-Yonge > Sherbourne", "Sherbourne > Bloor-Yonge", "Sherbourne > Castle Frank",
                "Castle Frank > Sherbourne", "Castle Frank > Broadview", "Broadview > Castle Frank"), closed,
                "Line 1 through St George and Bloor-Yonge, and Line 2 beyond the two end stations, keep running.");
    }

    @Test
    void alertsWithoutAPeriodOrARouteAreKeptForTheUseCaseToJudge() throws IOException {
        body = String.join("\n",
                "header {", "  timestamp: 1", "}",
                "entity {", "  id: \"1\"", "  alert {",
                "    informed_entity {", "      route_id: \"4\"", "      stop_id: \"13755\"", "    }",
                "    informed_entity {", "      route_id: \"4\"", "      stop_id: \"not-a-stop\"", "    }",
                "    informed_entity {", "      route_id: \"2\"", "    }",
                "    effect: NO_SERVICE",
                "    description_text {", "      translation {",
                "        text: \"Fallback wording\"", "      }", "    }",
                "  }", "}",
                "entity {", "  id: \"2\"", "  alert {",
                "    informed_entity {", "      stop_id: \"13757\"", "    }",
                "    effect: NO_SERVICE",
                "  }", "}",
                "entity {", "  id: \"3\"", "  alert {",
                "    informed_entity {", "      route_id: \"1\"", "    }",
                "    effect: SIGNIFICANT_DELAYS",
                "  }", "}", "");

        List<ServiceAlert> alerts = gateway().getNoServiceAlerts();

        assertEquals(3, alerts.size());
        assertEquals("Line 4", alerts.get(0).getLine());
        assertEquals(Set.of(BLOOR_YONGE), alerts.get(0).getStationIds(), "Unknown stops are skipped.");
        assertEquals("Fallback wording", alerts.get(0).getDescription());
        assertTrue(alerts.get(0).isActiveAt(Instant.EPOCH), "No period means the alert applies while it is published.");
        assertEquals("Line 2", alerts.get(1).getLine());
        assertTrue(alerts.get(1).getStationIds().isEmpty());
        assertEquals("", alerts.get(2).getLine(), "An alert without a route matches no connection.");
        assertEquals(Set.of(BAY), alerts.get(2).getStationIds());
        assertEquals("TTC service alert", alerts.get(2).getDescription());
    }

    @Test
    void reportsHttpErrors() {
        status = 503;
        assertTrue(assertThrows(IOException.class, () -> gateway().getNoServiceAlerts())
                .getMessage().contains("The TTC alert feed returned HTTP 503"));
    }

    @Test
    void reportsResponsesThatAreNotAFeed() {
        body = "<!DOCTYPE html>\n<html lang=\"en\">\n<body>Maintenance</body>\n</html>\n";
        assertThrows(IOException.class, () -> gateway().getNoServiceAlerts());

        body = "message: \"ok\"\n";
        assertTrue(assertThrows(IOException.class, () -> gateway().getNoServiceAlerts())
                .getMessage().contains("not an alert feed"));
    }

    @Test
    void reportsAnUnreachableFeed() {
        TtcAlertGateway gateway = gateway();
        server.stop(0);
        assertTrue(assertThrows(IOException.class, gateway::getNoServiceAlerts)
                .getMessage().contains("could not be reached"));
    }

    @Test
    void bundledStopTableMapsPlatformsToCatalogueStations() throws IOException {
        Map<String, String> stationByStop = TtcAlertGateway.readStationStops();
        Set<String> catalogue = new BundledStationRepository().load().stream().map(Station::getId)
                .collect(Collectors.toSet());

        assertEquals(BLOOR_YONGE, stationByStop.get("13755"), "Yonge Station - Westbound Platform");
        assertEquals(BLOOR_YONGE, stationByStop.get("13756"));
        assertTrue(catalogue.containsAll(stationByStop.values()));
        assertEquals(catalogue, Set.copyOf(stationByStop.values()), "Every catalogue station has a boarding stop.");
    }

    private TtcAlertGateway gateway() {
        try {
            return new TtcAlertGateway(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/alerts/subway?format=text"),
                    TtcAlertGateway.readStationStops());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void serve(HttpExchange exchange) throws IOException {
        try {
            requests.add(exchange.getRequestURI().toString());
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, status == 200 ? bytes.length : -1);
            if (status == 200) {
                exchange.getResponseBody().write(bytes);
            }
        } finally {
            exchange.close();
        }
    }
}
