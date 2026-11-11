package org.accessgraph.data_access;

import org.accessgraph.entity.GeoPosition;
import org.accessgraph.use_case.station_map.LocationGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BrowserLocationGatewayTest {
    private final BrowserLocationGateway gateway = new BrowserLocationGateway();
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicReference<GeoPosition> received = new AtomicReference<>();
    private final AtomicReference<LocationGateway.Failure> failure = new AtomicReference<>();

    @AfterEach
    void stop() { gateway.stop(); }

    private URI start() throws Exception {
        return gateway.start(new LocationGateway.Listener() {
            @Override public void onPosition(GeoPosition position) { received.set(position); }
            @Override public void onFailure(LocationGateway.Failure value) { failure.set(value); }
        });
    }

    @Test
    void localPermissionPageServesNoCoordinatesAndAcceptsAuthenticatedDeviceUpdates() throws Exception {
        URI page = start();
        assertEquals("127.0.0.1", page.getHost());
        assertTrue(page.getFragment().length() >= 40);
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(page.resolve("/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Start sharing location"));
        assertFalse(response.body().contains(page.getFragment()));
        assertTrue(response.headers().firstValue("Content-Security-Policy").orElseThrow().contains("frame-ancestors 'none'"));
        assertNull(received.get(), "Opening the page alone must not create or estimate a location.");
        assertEquals(204, post(page, "/position", positionBody(Instant.now()), origin(page), page.getFragment()).statusCode());
        assertEquals(43.65, received.get().getLatitude());
        assertEquals(18, received.get().getAccuracyMetres());
        assertEquals(204, post(page, "/status", "code=PERMISSION_DENIED", origin(page), page.getFragment()).statusCode());
        assertEquals(LocationGateway.Failure.PERMISSION_DENIED, failure.get());
    }

    @Test
    void rejectsCrossOriginMissingOrWrongTokensAndWrongContentType() throws Exception {
        URI page = start();
        assertEquals(403, post(page, "/position", positionBody(Instant.now()), "https://example.com", page.getFragment()).statusCode());
        assertEquals(403, post(page, "/position", positionBody(Instant.now()), origin(page), "incorrect").statusCode());
        HttpRequest noToken = HttpRequest.newBuilder(page.resolve("/position"))
                .header("Origin", origin(page)).POST(HttpRequest.BodyPublishers.ofString(positionBody(Instant.now()))).build();
        assertEquals(403, client.send(noToken, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest noType = HttpRequest.newBuilder(page.resolve("/position"))
                .header("Origin", origin(page)).header("X-AccessGraph-Token", page.getFragment())
                .POST(HttpRequest.BodyPublishers.ofString(positionBody(Instant.now()))).build();
        assertEquals(415, client.send(noType, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertNull(received.get());
    }

    @Test
    void rejectsStaleInvalidDuplicateAndOversizedPositions() throws Exception {
        URI page = start();
        for (String body : new String[]{positionBody(Instant.now().minusSeconds(120)),
                positionBody(Instant.now()).replace("latitude=43.65", "latitude=NaN"),
                positionBody(Instant.now()) + "&latitude=45", "code=unexpected"}) {
            assertEquals(400, post(page, "/position", body, origin(page), page.getFragment()).statusCode());
        }
        assertEquals(413, post(page, "/position", "a=" + "x".repeat(1100), origin(page), page.getFragment()).statusCode());
        assertNull(received.get());
    }

    @Test
    void restartingInvalidatesTheOldPermissionSessionAndStoppingClosesTheListener() throws Exception {
        URI old = start();
        URI current = start();
        assertNotEquals(old.getFragment(), current.getFragment());
        assertEquals(403, post(current, "/session", "", origin(current), old.getFragment()).statusCode());
        assertEquals(204, post(current, "/session", "", origin(current), current.getFragment()).statusCode());
        gateway.stop();
        assertThrows(java.io.IOException.class,
                () -> post(current, "/session", "", origin(current), current.getFragment()));
    }

    private HttpResponse<String> post(URI page, String path, String body, String origin, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(page.resolve(path)).header("Origin", origin)
                .header("X-AccessGraph-Token", token).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String origin(URI page) { return "http://" + page.getAuthority(); }

    private String positionBody(Instant when) {
        return "latitude=43.65&longitude=-79.38&accuracy=18&timestamp=" + when.toEpochMilli();
    }
}
