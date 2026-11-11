package org.accessgraph.data_access;

import org.accessgraph.entity.Station;
import org.accessgraph.use_case.station_map.MapImageGateway;
import org.accessgraph.use_case.station_map.MapRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/** Google Static Maps transport. Keys and request URLs are never included in user-facing errors. */
public final class GoogleStaticMapsGateway implements MapImageGateway {
    private static final URI ENDPOINT = URI.create("https://maps.googleapis.com/maps/api/staticmap");
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private final HttpClient client;
    private final Supplier<String> apiKey;
    private final URI endpoint;

    public GoogleStaticMapsGateway(HttpClient client, String apiKey) {
        this(client, apiKey, ENDPOINT);
    }

    public GoogleStaticMapsGateway(HttpClient client, Supplier<String> apiKey) {
        this.client = client;
        this.apiKey = apiKey;
        this.endpoint = ENDPOINT;
    }

    GoogleStaticMapsGateway(HttpClient client, String apiKey, URI endpoint) {
        this.client = client;
        this.apiKey = () -> apiKey == null ? "" : apiKey.trim();
        this.endpoint = endpoint;
    }

    @Override
    public boolean isConfigured() { return !currentKey().isEmpty(); }

    private String currentKey() {
        String value = apiKey.get();
        return value == null ? "" : value.trim();
    }

    URI requestUri(List<Station> stations, Station focus) {
        return requestUri(new MapRequest(stations, focus, null, ""));
    }

    URI requestUri(MapRequest request) {
        return requestUri(request, currentKey());
    }

    private URI requestUri(MapRequest request, String key) {
        List<Station> stations = request.getStations();
        Station focus = request.getFocus();
        if (key.isEmpty()) { throw new IllegalStateException("Google Maps API key is not configured."); }
        StringBuilder query = new StringBuilder("?size=640x440&scale=1&format=png&maptype=roadmap");
        for (Station.Accessibility status : Station.Accessibility.values()) {
            String coordinates = stations.stream().filter(station -> station.getAccessibility() == status)
                    .filter(station -> station != focus).map(Station::getCoordinates).collect(Collectors.joining("|"));
            if (!coordinates.isEmpty()) {
                String color = status == Station.Accessibility.CONFIRMED ? "0x006B68"
                        : status == Station.Accessibility.LIMITED ? "0xA15B00" : "0x657681";
                query.append("&markers=").append(encode("color:" + color + "|size:small|" + coordinates));
            }
        }
        if (focus != null) {
            query.append("&markers=").append(encode("color:red|label:S|" + focus.getCoordinates()));
            query.append("&center=").append(encode(focus.getCoordinates())).append("&zoom=15");
        }
        if (request.getPosition() != null) {
            query.append("&markers=").append(encode("color:blue|label:U|" + request.getPosition().getCoordinates()));
        }
        if (!request.getDestination().isEmpty()) {
            query.append("&markers=").append(encode("color:red|label:D|" + request.getDestination()));
        }
        query.append("&key=").append(encode(key));
        if (query.length() + endpoint.toString().length() > 16384) {
            throw new IllegalArgumentException("Too many map markers. Narrow the station filter.");
        }
        return URI.create(endpoint + query.toString());
    }

    @Override
    public byte[] fetch(List<Station> stations, Station focus) throws IOException, InterruptedException {
        return fetch(new MapRequest(stations, focus, null, ""));
    }

    @Override
    public byte[] fetch(MapRequest map) throws IOException, InterruptedException {
        String key = currentKey();
        if (key.isEmpty()) { throw new IOException("Add a key in Settings → Google Maps to load this map."); }
        HttpRequest request = HttpRequest.newBuilder(requestUri(map, key))
                .timeout(Duration.ofSeconds(30)).GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        HttpResponse<byte[]> response;
        try {
            // Bound the complete download, including a body that stalls after successful response headers.
            response = pending.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw new IOException("Google Maps timed out. Check your connection and try again.");
        } catch (InterruptedException exception) {
            pending.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            // HttpClient exceptions can contain a URL carrying the key. Do not propagate them.
            throw new IOException("Google Maps could not be reached. Check your connection and try again.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Google Maps returned HTTP " + response.statusCode()
                    + ". Check the API key, Maps Static API activation, billing, and quota.");
        }
        if (response.headers().firstValue("X-Staticmap-API-Warning").isPresent()) {
            throw new IOException("Google could not render every pin. Check the destination address or narrow the station filter.");
        }
        if (!response.headers().firstValue("Content-Type").orElse("").startsWith("image/png")) {
            throw new IOException("Google Maps returned an unexpected response instead of a map image.");
        }
        byte[] image = response.body();
        if (image.length == 0 || image.length > MAX_IMAGE_BYTES) {
            throw new IOException("Google Maps returned an invalid map image size.");
        }
        return image;
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
