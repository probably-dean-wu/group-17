package org.accessgraph.data_access;

import org.accessgraph.data_access.GtfsRealtimeText.Block;
import org.accessgraph.entity.ServiceAlert;
import org.accessgraph.use_case.live_alerts.ServiceAlertDataAccessInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Downloads the TTC's GTFS-Realtime subway alerts and turns the no-service alerts into
 * {@link ServiceAlert}s. Alerts name platforms by GTFS stop id; the bundled stop table translates them
 * to the station ids of the catalogue, and a route id becomes the line name used by the connections.
 */
public final class TtcAlertGateway implements ServiceAlertDataAccessInterface {
    private static final String USER_AGENT = "AccessGraph/1.0";
    private static final String DEFAULT_FEED_URL = "https://gtfsrt.ttc.ca/alerts/subway?format=text";
    private static final String STATION_STOPS = "/data/ttc-station-stops.tsv";
    private static final String NO_SERVICE = "NO_SERVICE";
    private static final String LINE_PREFIX = "Line ";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final HttpClient client;
    private final URI feed;
    private final Map<String, String> stationByStop;

    /** Reads the live TTC feed, or the address in the {@code accessgraph.alerts.url} system property. */
    public TtcAlertGateway(HttpClient client) throws IOException {
        this(client, URI.create(System.getProperty("accessgraph.alerts.url", DEFAULT_FEED_URL)),
                readStationStops());
    }

    TtcAlertGateway(HttpClient client, URI feed, Map<String, String> stationByStop) {
        this.client = client;
        this.feed = feed;
        this.stationByStop = Map.copyOf(stationByStop);
    }

    @Override
    public List<ServiceAlert> getNoServiceAlerts() throws IOException {
        Block message = GtfsRealtimeText.parse(download());
        if (message.blocks("header").isEmpty()) {
            throw new IOException("The TTC alert feed sent a response that is not an alert feed.");
        }
        List<ServiceAlert> alerts = new ArrayList<>();
        for (Block entity : message.blocks("entity")) {
            for (Block alert : entity.blocks("alert")) {
                if (NO_SERVICE.equals(alert.value("effect"))) {
                    alerts.addAll(toServiceAlerts(alert));
                }
            }
        }
        return alerts;
    }

    /** Reads the bundled table of GTFS boarding stops and the catalogue station each one belongs to. */
    static Map<String, String> readStationStops() throws IOException {
        InputStream stream = TtcAlertGateway.class.getResourceAsStream(STATION_STOPS);
        if (stream == null) {
            throw new IOException("The bundled table of station stops is missing.");
        }
        Map<String, String> stationByStop = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            if (!"stop_id\tstation_id".equals(reader.readLine())) {
                throw new IOException("The bundled table of station stops has an unsupported format.");
            }
            String row;
            while ((row = reader.readLine()) != null) {
                String[] values = row.split("\t");
                if (values.length != 2 || stationByStop.put(values[0], values[1]) != null) {
                    throw new IOException("Invalid or duplicate row in the table of station stops: " + row);
                }
            }
        }
        return stationByStop;
    }

    private String download() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(feed).timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT).header("Accept", "text/plain").build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException exception) {
            throw new IOException("The TTC alert feed timed out. Check your connection and try again.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Loading the TTC alert feed was interrupted.", exception);
        } catch (IOException exception) {
            throw new IOException("The TTC alert feed could not be reached. Check your connection and try again.",
                    exception);
        }
        if (response.statusCode() != 200) {
            throw new IOException("The TTC alert feed returned HTTP " + response.statusCode() + ". Try again later.");
        }
        return response.body();
    }

    /**
     * One alert can name several lines and several periods, so it becomes one notice for each pair. An
     * alert that names no line gets an empty line name: it matches no connection, which lets the use case
     * report it as unmatched instead of guessing.
     */
    private List<ServiceAlert> toServiceAlerts(Block alert) throws IOException {
        Map<String, Set<String>> stationsByLine = new LinkedHashMap<>();
        for (Block informed : alert.blocks("informed_entity")) {
            String route = informed.value("route_id");
            Set<String> stations = stationsByLine.computeIfAbsent(route == null ? "" : LINE_PREFIX + route,
                    key -> new LinkedHashSet<>());
            String stop = informed.value("stop_id");
            if (stop != null && stationByStop.containsKey(stop)) {
                stations.add(stationByStop.get(stop));
            }
        }
        String description = text(alert, "header_text");
        if (description == null) {
            description = text(alert, "description_text");
        }
        if (description == null) {
            description = "TTC service alert";
        }

        List<ServiceAlert> alerts = new ArrayList<>();
        for (Map.Entry<String, Set<String>> stretch : stationsByLine.entrySet()) {
            List<Block> periods = alert.blocks("active_period");
            if (periods.isEmpty()) {
                alerts.add(new ServiceAlert(stretch.getKey(), stretch.getValue(), description, null, null));
            }
            for (Block period : periods) {
                alerts.add(new ServiceAlert(stretch.getKey(), stretch.getValue(), description,
                        time(period.value("start")), time(period.value("end"))));
            }
        }
        return alerts;
    }

    /** Returns the English wording of a translated field, or its first wording, or null when it is absent. */
    private static String text(Block alert, String field) {
        String first = null;
        for (Block translated : alert.blocks(field)) {
            for (Block translation : translated.blocks("translation")) {
                String text = translation.value("text");
                if (text != null && "en".equals(translation.value("language"))) {
                    return text.trim();
                }
                if (first == null && text != null) {
                    first = text.trim();
                }
            }
        }
        return first;
    }

    private static Instant time(String epochSeconds) throws IOException {
        if (epochSeconds == null) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(epochSeconds));
        } catch (RuntimeException exception) {
            throw new IOException("The TTC alert feed has a time that cannot be read.", exception);
        }
    }
}
