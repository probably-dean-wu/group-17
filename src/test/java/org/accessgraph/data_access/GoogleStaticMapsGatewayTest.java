package org.accessgraph.data_access;

import com.sun.net.httpserver.HttpServer;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.GeoPosition;
import org.accessgraph.use_case.station_map.MapRequest;
import org.accessgraph.interface_adapter.GoogleMapsLinks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleStaticMapsGatewayTest {
    private static final String TEST_KEY = "test-only-key";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    private HttpServer server;
    private GoogleStaticMapsGateway gateway;
    private List<Station> stations;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        gateway = new GoogleStaticMapsGateway(HttpClient.newHttpClient(), TEST_KEY,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/map"));
        stations = new BundledStationRepository().load();
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @Test
    void requestFitsAllStationsAndKeepsAccessibilityCategoriesDistinct() {
        String query = URLDecoder.decode(gateway.requestUri(stations, null).getRawQuery(), StandardCharsets.UTF_8);
        for (Station station : stations) { assertTrue(query.contains(station.getCoordinates())); }
        assertTrue(query.contains("color:0x006B68"));
        assertTrue(query.contains("color:0xA15B00"));
        assertTrue(query.contains("color:0x657681"));
        assertFalse(query.contains("center="), "Overview must fit all supplied pins.");
        assertFalse(query.contains("zoom="));
        assertTrue(gateway.requestUri(stations, null).toString().length() < 16384);
        String focused = URLDecoder.decode(gateway.requestUri(stations, stations.get(0)).getRawQuery(),
                StandardCharsets.UTF_8);
        assertTrue(focused.contains("label:S|" + stations.get(0).getCoordinates()));
        assertTrue(focused.contains("zoom=15"));
    }

    @Test
    void downloadsImageFromHttpWithoutUsingAnyLiveService() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        server.createContext("/map", exchange -> {
            request.set(exchange.getRequestURI().getRawQuery());
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, PNG.length);
            try (java.io.OutputStream body = exchange.getResponseBody()) { body.write(PNG); }
        });
        assertArrayEquals(PNG, gateway.fetch(stations, null));
        assertTrue(request.get().contains("markers="));
    }

    @Test
    void rejectsFailedResponsesAndIncompleteMapsWithoutExposingKeys() {
        server.createContext("/map", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        IOException error = assertThrows(IOException.class, () -> gateway.fetch(stations, null));
        assertTrue(error.getMessage().contains("403"));
        assertFalse(error.getMessage().contains(TEST_KEY));
        server.removeContext("/map");
        server.createContext("/map", exchange -> {
            exchange.getResponseHeaders().set("X-Staticmap-API-Warning", "Some markers were omitted");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        assertThrows(IOException.class, () -> gateway.fetch(stations, null));
    }

    @Test
    void missingKeyFailsBeforeAnyRequestAndBrowserLinksNeedNoKey() {
        GoogleStaticMapsGateway missing = new GoogleStaticMapsGateway(HttpClient.newHttpClient(), " ");
        assertFalse(missing.isConfigured());
        assertThrows(IOException.class, () -> missing.fetch(stations, null));
        URI link = GoogleMapsLinks.station(stations.get(0));
        assertTrue(link.toString().startsWith("https://www.google.com/maps/search/?api=1&query="));
        assertFalse(link.toString().contains("key="));
    }

    @Test
    void journeyPinsAreEncodedAlongsideStationDataAndDoNotBecomeQueryParameters() {
        GeoPosition position = new GeoPosition(43.65, -79.38, 12, Instant.now());
        String address = "100 Queen St W, Toronto & City Hall";
        String query = URLDecoder.decode(gateway.requestUri(new MapRequest(stations, null, position, address))
                .getRawQuery(), StandardCharsets.UTF_8);
        assertTrue(query.contains("color:blue|label:U|43.65,-79.38"));
        assertTrue(query.contains("color:red|label:D|" + address));
        assertFalse(query.contains("center="));
        assertThrows(IllegalArgumentException.class,
                () -> new MapRequest(stations, null, position, "Toronto|another marker"));
        URI journey = GoogleMapsLinks.journey(position, address);
        assertTrue(journey.getRawQuery().contains("%26"));
        assertTrue(journey.getRawQuery().contains("origin="));
        assertFalse(GoogleMapsLinks.journey(null, address).getRawQuery().contains("origin="));
    }
}
