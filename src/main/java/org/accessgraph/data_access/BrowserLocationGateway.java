package org.accessgraph.data_access;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.accessgraph.entity.GeoPosition;
import org.accessgraph.use_case.station_map.LocationGateway;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Permission-based browser location bridge. Binds only to loopback and keeps no location history. */
public final class BrowserLocationGateway implements LocationGateway {
    private Session current;

    @Override
    public synchronized URI start(Listener listener) throws IOException {
        stop();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newFixedThreadPool(2, action -> {
            Thread thread = new Thread(action, "accessgraph-location");
            thread.setDaemon(true);
            return thread;
        });
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Session session = new Session(server, executor, listener, token);
        server.setExecutor(executor);
        server.createContext("/", exchange -> handle(session, exchange));
        try {
            server.start();
            current = session;
            // A fragment avoids sending the permission-session token in HTTP URLs or referrers.
            return URI.create(session.origin + "/#" + token);
        } catch (RuntimeException exception) {
            session.close();
            throw new IOException("Could not start the local location-permission page.");
        }
    }

    @Override
    public synchronized void stop() {
        if (current != null) {
            current.close();
            current = null;
        }
    }

    private void handle(Session session, HttpExchange exchange) throws IOException {
        try {
            if (!session.active.get() || !session.host.equals(exchange.getRequestHeaders().getFirst("Host"))) {
                respond(exchange, 403, "text/plain", "Session unavailable");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (Set.of("/", "/location.js", "/location.css").contains(path)) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    respond(exchange, 405, "text/plain", "GET required");
                    return;
                }
                String resource = "/location/" + ("/".equals(path) ? "index.html" : path.substring(1));
                String type = "/".equals(path) ? "text/html" : path.endsWith(".js")
                        ? "text/javascript" : "text/css";
                try (InputStream input = getClass().getResourceAsStream(resource)) {
                    if (input == null) { respond(exchange, 404, "text/plain", "Page unavailable"); }
                    else { respond(exchange, 200, type, new String(input.readAllBytes(), StandardCharsets.UTF_8)); }
                }
                return;
            }
            if (!Set.of("/position", "/status", "/session").contains(path)) {
                respond(exchange, 404, "text/plain", "Not found");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "text/plain", "POST required");
                return;
            }
            String supplied = exchange.getRequestHeaders().getFirst("X-AccessGraph-Token");
            if (!session.origin.equals(exchange.getRequestHeaders().getFirst("Origin")) || supplied == null
                    || !MessageDigest.isEqual(session.token.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) {
                respond(exchange, 403, "text/plain", "Permission session required");
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT)
                    .startsWith("application/x-www-form-urlencoded")) {
                respond(exchange, 415, "text/plain", "Form data required");
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(1025);
            if (body.length > 1024) { respond(exchange, 413, "text/plain", "Request too large"); return; }
            Map<String, String> fields = fields(new String(body, StandardCharsets.UTF_8));
            if ("/position".equals(path)) {
                if (!fields.keySet().equals(Set.of("latitude", "longitude", "accuracy", "timestamp"))) {
                    throw new IllegalArgumentException("Missing position fields");
                }
                GeoPosition position = new GeoPosition(Double.parseDouble(fields.get("latitude")),
                        Double.parseDouble(fields.get("longitude")), Double.parseDouble(fields.get("accuracy")),
                        Instant.ofEpochMilli(Long.parseLong(fields.get("timestamp"))));
                if (!position.isRecent(Instant.now())) { throw new IllegalArgumentException("Stale position"); }
                if (session.active.get()) { session.listener.onPosition(position); }
            } else if ("/status".equals(path)) {
                if (!fields.keySet().equals(Set.of("code"))) { throw new IllegalArgumentException("Missing status"); }
                Failure failure = Failure.valueOf(fields.get("code"));
                if (session.active.get()) { session.listener.onFailure(failure); }
            } else if (!fields.isEmpty()) {
                throw new IllegalArgumentException("Unexpected session data");
            }
            respond(exchange, 204, "text/plain", "");
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            respond(exchange, 400, "text/plain", "Invalid location update");
        } finally {
            exchange.close();
        }
    }

    private Map<String, String> fields(String body) {
        Map<String, String> result = new HashMap<>();
        if (!body.isEmpty()) {
            for (String field : body.split("&")) {
                String[] pair = field.split("=", 2);
                if (pair.length != 2) { throw new IllegalArgumentException("Invalid form field"); }
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                if (result.put(key, value) != null) { throw new IllegalArgumentException("Duplicate field"); }
            }
        }
        return result;
    }

    private void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Permissions-Policy", "geolocation=(self)");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; script-src 'self'; "
                + "style-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) { exchange.getResponseBody().write(bytes); }
    }

    private static final class Session {
        private final HttpServer server;
        private final ExecutorService executor;
        private final Listener listener;
        private final String token;
        private final String host;
        private final String origin;
        private final AtomicBoolean active = new AtomicBoolean(true);

        Session(HttpServer server, ExecutorService executor, Listener listener, String token) {
            this.server = server;
            this.executor = executor;
            this.listener = listener;
            this.token = token;
            host = "127.0.0.1:" + server.getAddress().getPort();
            origin = "http://" + host;
        }

        void close() {
            active.set(false);
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
