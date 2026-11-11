package org.accessgraph.view;

import org.accessgraph.data_access.BundledStationRepository;
import org.accessgraph.entity.GeoPosition;
import org.accessgraph.entity.Station;
import org.accessgraph.use_case.station_map.LocationGateway;
import org.accessgraph.use_case.station_map.MapImageGateway;
import org.accessgraph.use_case.station_map.MapRequest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JourneyLocationTest {
    @Test
    void destinationAndPermissionBasedLiveLocationReachTheMapAndStopClearsThem() throws Exception {
        FakeLocations locations = new FakeLocations();
        FakeMaps maps = new FakeMaps();
        AtomicReference<URI> opened = new AtomicReference<>();
        AtomicReference<StationMapPanel> view = new AtomicReference<>();
        List<Station> stations = new BundledStationRepository().load();
        SwingUtilities.invokeAndWait(() -> {
            StationMapPanel panel = new StationMapPanel(stations, maps, opened::set, locations);
            view.set(panel);
            destination(panel).setText("100 Queen St W, Toronto");
            button(panel, "Show destination on map").doClick();
        });
        awaitMap(maps, false);
        assertEquals("100 Queen St W, Toronto", maps.last.get().getDestination());
        SwingUtilities.invokeAndWait(() -> button(view.get(), "Use my location").doClick());
        assertEquals("127.0.0.1", opened.get().getHost());
        assertNull(maps.last.get().getPosition(), "Location must never be guessed before device permission.");
        locations.listener.onPosition(new GeoPosition(44, -79, 17, Instant.now().minusSeconds(120)));
        SwingUtilities.invokeAndWait(() -> assertNull(maps.last.get().getPosition(), "Stale fixes must not be displayed."));
        locations.listener.onPosition(new GeoPosition(43.65, -79.38, 17, Instant.now()));
        awaitMap(maps, true);
        locations.listener.onPosition(new GeoPosition(43.1, -79.1, 30, Instant.now().minusSeconds(10)));
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(find(view.get(), JTextArea.class).stream().anyMatch(text -> text.getText().contains("±17 m")));
            button(view.get(), "Open journey in Google Maps").doClick();
            assertTrue(opened.get().getQuery().contains("origin=43.65,-79.38"), "Older fixes must not replace newer positions.");
            button(view.get(), "Stop sharing").doClick();
            assertTrue(locations.stopped);
            assertFalse(button(view.get(), "Stop sharing").isEnabled());
            assertTrue(button(view.get(), "Use my location").isEnabled());
        });
        locations.listener.onPosition(new GeoPosition(44, -79, 10, Instant.now()));
        SwingUtilities.invokeAndWait(() -> {
            button(view.get(), "Open journey in Google Maps").doClick();
            assertFalse(opened.get().getQuery().contains("origin="), "Stopped callbacks must not restore location.");
            view.get().close();
        });
    }

    @Test
    void deniedPermissionAndInvalidAddressesRemainUsableWithoutAnApiKey() throws Exception {
        FakeLocations locations = new FakeLocations();
        AtomicReference<URI> opened = new AtomicReference<>();
        AtomicReference<StationMapPanel> view = new AtomicReference<>();
        List<Station> stations = new BundledStationRepository().load();
        SwingUtilities.invokeAndWait(() -> {
            StationMapPanel panel = new StationMapPanel(stations, new MapImageGateway() {
                @Override public boolean isConfigured() { return false; }
                @Override public byte[] fetch(List<Station> rows, Station focus) { throw new AssertionError(); }
            }, opened::set, locations);
            view.set(panel);
            destination(panel).setText("Toronto|unexpected marker");
            button(panel, "Open journey in Google Maps").doClick();
            assertNull(opened.get());
            destination(panel).setText("100 Queen St W, Toronto");
            button(panel, "Use my location").doClick();
        });
        locations.listener.onFailure(LocationGateway.Failure.PERMISSION_DENIED);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(find(view.get(), JTextArea.class).stream()
                    .anyMatch(text -> text.getText().startsWith("Location permission denied")));
            button(view.get(), "Open journey in Google Maps").doClick();
            assertEquals("www.google.com", opened.get().getHost());
            assertFalse(opened.get().getQuery().contains("origin="));
            assertTrue(locations.stopped);
            view.get().close();
        });
    }

    private void awaitMap(FakeMaps maps, boolean withLocation) throws Exception {
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < until) {
            MapRequest request = maps.last.get();
            if (request != null && (request.getPosition() != null) == withLocation) {
                // Drain Swing's delivery queue before the next user action.
                Thread.sleep(60);
                SwingUtilities.invokeAndWait(() -> { });
                return;
            }
            Thread.sleep(20);
        }
        fail("The expected journey map request was not received.");
    }

    private static final class FakeLocations implements LocationGateway {
        private Listener listener;
        private boolean stopped;
        @Override public URI start(Listener value) {
            listener = value;
            stopped = false;
            return URI.create("http://127.0.0.1:8080/#test-permission-session");
        }
        @Override public void stop() { stopped = true; }
    }

    private static final class FakeMaps implements MapImageGateway {
        private final AtomicReference<MapRequest> last = new AtomicReference<>();
        @Override public boolean isConfigured() { return true; }
        @Override public byte[] fetch(List<Station> rows, Station focus) { throw new AssertionError(); }
        @Override public byte[] fetch(MapRequest request) throws java.io.IOException {
            assertFalse(SwingUtilities.isEventDispatchThread());
            last.set(request);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", bytes);
            return bytes.toByteArray();
        }
    }

    private static JTextField destination(Container root) {
        return find(root, JTextField.class).stream().filter(field -> "Destination address".equals(
                field.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
    }

    private static JButton button(Container root, String text) {
        return find(root, JButton.class).stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static <T> List<T> find(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) { result.add(type.cast(child)); }
            if (child instanceof Container) { result.addAll(find((Container) child, type)); }
        }
        return result;
    }
}
