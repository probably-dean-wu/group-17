package org.accessgraph.view;

import org.accessgraph.data_access.BrowserLocationGateway;
import org.accessgraph.data_access.BundledStationRepository;
import org.accessgraph.data_access.GoogleStaticMapsGateway;
import org.accessgraph.interface_adapter.ApiKeySettings;
import org.accessgraph.interface_adapter.PrototypeController;
import org.accessgraph.interface_adapter.PrototypeState;
import org.accessgraph.use_case.settings.ApiKeyStore;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class MapApiKeyPanelTest {
    @Test
    void keyStaysMaskedAndSavingOrRemovingUpdatesMapAccessWithoutRestarting() throws Exception {
        ApiKeySettings settings = ApiKeySettings.sessionOnly("");
        GoogleStaticMapsGateway gateway = new GoogleStaticMapsGateway(HttpClient.newHttpClient(), settings::keyForRequest);
        List<org.accessgraph.entity.Station> stations = new BundledStationRepository().load();
        AtomicReference<AccessGraphPanel> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PrototypeState state = new PrototypeState();
            StationMapPanel map = new StationMapPanel(stations, gateway, ignored -> { }, new BrowserLocationGateway());
            AccessGraphPanel panel = new AccessGraphPanel(state, new PrototypeController(state), map, settings);
            view.set(panel);
            assertFalse(button(panel, "Show listed stations").isEnabled());
            button(panel, "Map settings").doClick();
            assertEquals(PrototypeState.Screen.PROFILE, state.getScreen());
            JPasswordField input = find(panel, JPasswordField.class).get(0);
            assertNotEquals(0, input.getEchoChar());
            assertEquals(0, input.getPassword().length, "Existing credentials are never prefilled.");
            input.setText("settings-test-key");
            checkbox(panel, "Show key").doClick();
            assertEquals(0, input.getEchoChar());
            button(panel, "Save key").doClick();
            assertNotEquals(0, input.getEchoChar(), "Save must immediately hide a revealed input.");
        });
        await(() -> settings.isConfigured() && find(view.get(), JPasswordField.class).get(0).getPassword().length == 0);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(gateway.isConfigured());
            assertTrue(button(view.get(), "Show listed stations").isEnabled());
            assertFalse(checkbox(view.get(), "Show key").isSelected());
            assertFalse(find(view.get(), JTextArea.class).stream().anyMatch(text -> text.getText().contains("settings-test-key")));
            button(view.get(), "Remove key").doClick();
        });
        await(() -> !settings.isConfigured() && !button(view.get(), "Show listed stations").isEnabled());
        SwingUtilities.invokeAndWait(() -> view.get().close());
    }

    @Test
    void secureStorageRunsOffTheEventThreadAndFailuresNeverEchoAKey() throws Exception {
        AtomicBoolean calledOnEdt = new AtomicBoolean();
        ApiKeySettings settings = new ApiKeySettings("existing-test-key", new ApiKeyStore() {
            @Override public boolean isAvailable() { return true; }
            @Override public String load() { return ""; }
            @Override public void save(String value) throws IOException {
                calledOnEdt.set(SwingUtilities.isEventDispatchThread());
                throw new IOException("Do not display this secret: " + value);
            }
            @Override public void delete() { }
        });
        AtomicReference<MapApiKeyPanel> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MapApiKeyPanel panel = new MapApiKeyPanel(settings);
            view.set(panel);
            find(panel, JPasswordField.class).get(0).setText("replacement-test-key");
            button(panel, "Save key").doClick();
        });
        await(() -> find(view.get(), JTextArea.class).stream().anyMatch(text -> text.getText().startsWith("Could not save")));
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(calledOnEdt.get());
            assertEquals("existing-test-key", settings.keyForRequest());
            assertNotEquals(0, find(view.get(), JPasswordField.class).get(0).getEchoChar());
            assertFalse(find(view.get(), JTextArea.class).stream().anyMatch(text -> text.getText().contains("replacement-test-key")));
            view.get().close();
        });
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        AtomicBoolean done = new AtomicBoolean();
        while (!done.get() && System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> done.set(condition.getAsBoolean()));
            if (!done.get()) { Thread.sleep(20); }
        }
        assertTrue(done.get(), "Settings operation did not finish.");
    }

    private static JButton button(Container root, String text) {
        return find(root, JButton.class).stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static JCheckBox checkbox(Container root, String text) {
        return find(root, JCheckBox.class).stream().filter(box -> text.equals(box.getText())).findFirst().orElseThrow();
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
