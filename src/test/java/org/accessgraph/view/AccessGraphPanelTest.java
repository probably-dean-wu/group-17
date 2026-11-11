package org.accessgraph.view;

import org.accessgraph.interface_adapter.PrototypeController;
import org.accessgraph.interface_adapter.PrototypeState;
import org.accessgraph.interface_adapter.PrototypeState.Screen;
import org.accessgraph.interface_adapter.ApiKeySettings;
import org.accessgraph.data_access.BundledStationRepository;
import org.accessgraph.data_access.BrowserLocationGateway;
import org.accessgraph.data_access.GoogleStaticMapsGateway;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessGraphPanelTest {
    @Test
    void swingControlsSupportTheCompleteSessionWorkflow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PrototypeState state = new PrototypeState();
            AccessGraphPanel panel = panel(state, new PrototypeController(state));
            assertEquals(5, find(panel, JTabbedPane.class).get(0).getTabCount());
            assertEquals("Plan journey", find(panel, JTabbedPane.class).get(0).getTitleAt(0));
            assertEquals(Screen.STATION_MAP, state.getScreen());
            button(panel, "Find route").doClick();
            assertTrue(state.isError(Screen.PLANNER));

            List<JComboBox> stations = find(panel, JComboBox.class);
            stations.get(0).setSelectedItem("Union");
            stations.get(1).setSelectedItem("Finch");
            button(panel, "Swap stations").doClick();
            assertEquals("Finch", state.getOrigin());
            button(panel, "Find route").doClick();
            assertTrue(state.isRequestReviewed());

            named(panel, JTextField.class, "Trip name (session only)").setText("Home");
            button(panel, "Save station pair").doClick();
            assertEquals(1, state.getSavedTrips().size());
            button(panel, "Edit preferences").doClick();
            assertEquals(Screen.PROFILE, state.getScreen());
            JCheckBox wheelchair = checkbox(panel, "Require wheelchair-accessible connections");
            JCheckBox stairs = checkbox(panel, "Allow connections that require stairs");
            assertFalse(stairs.isEnabled());
            wheelchair.doClick();
            stairs.doClick();
            button(panel, "Apply preferences").doClick();
            assertTrue(state.getProfile().isStairsAllowed());
            assertFalse(state.isRequestReviewed());

            find(panel, JTabbedPane.class).get(0).setSelectedIndex(Screen.SAVED_TRIPS.ordinal());
            assertFalse(button(panel, "Load selected trip").isEnabled());
            named(panel, JList.class, "Saved station pairs").setSelectedIndex(0);
            button(panel, "Load selected trip").doClick();
            assertEquals(Screen.PLANNER, state.getScreen());
            assertTrue(state.isRequestReviewed());
            assertTrue(state.getProfile().isStairsAllowed());

            find(panel, JTabbedPane.class).get(0).setSelectedIndex(Screen.DISRUPTIONS.ordinal());
            named(panel, JCheckBox.class, "Sample elevator connection: mark unavailable").doClick();
            assertEquals(1, state.getActiveDisruptionCount());
            assertFalse(state.isRequestReviewed());
            button(panel, "Restore all sample connections").doClick();
            assertEquals(0, state.getActiveDisruptionCount());

            find(panel, JTabbedPane.class).get(0).setSelectedIndex(Screen.SAVED_TRIPS.ordinal());
            named(panel, JList.class, "Saved station pairs").setSelectedIndex(0);
            button(panel, "Delete selected trip").doClick();
            assertTrue(state.getSavedTrips().isEmpty());
            assertFalse(button(panel, "Delete selected trip").isEnabled());
        });
    }

    @Test
    void invalidTypedTransferLimitDoesNotReplaceAppliedPreferences() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PrototypeState state = new PrototypeState();
            AccessGraphPanel panel = panel(state, new PrototypeController(state));
            JSpinner spinner = find(panel, JSpinner.class).get(0);
            for (String invalid : new String[]{"7", "-1", "abc", "1.5"}) {
                ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(invalid);
                button(panel, "Apply preferences").doClick();
                assertEquals(2, state.getProfile().getMaxTransfers());
            }
        });
    }

    @Test
    void resizingScreensKeepsParagraphsVisibleAndPagesWithinTheViewport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PrototypeState state = new PrototypeState();
            PrototypeController controller = new PrototypeController(state);
            AccessGraphPanel panel = panel(state, controller);
            controller.selectJourney("Union", "Bloor–Yonge");
            controller.requestRoute();
            controller.saveTrip("Campus");
            for (Screen screen : Screen.values()) {
                controller.navigate(screen);
                for (int width : new int[]{1200, 1024}) {
                    panel.setSize(width, 880);
                    for (int pass = 0; pass < 6; pass++) { layoutTree(panel); }
                    for (JTextArea text : find(panel, JTextArea.class)) {
                        if (visibleInPanel(text, panel)) {
                            assertTrue(text.getHeight() >= text.getPreferredSize().height,
                                    "Wrapped text was clipped: " + text.getText());
                        }
                    }
                    for (JScrollPane scroll : find(panel, JScrollPane.class)) {
                        if (visibleInPanel(scroll, panel) && !(scroll.getViewport().getView() instanceof JList)) {
                            assertEquals(scroll.getViewport().getExtentSize().width,
                                    scroll.getViewport().getView().getWidth());
                        }
                    }
                }
            }
        });
    }

    private static boolean visibleInPanel(Component component, Container root) {
        for (Component current = component; current != root; current = current.getParent()) {
            if (current == null || !current.isVisible()) { return false; }
        }
        return true;
    }

    private static AccessGraphPanel panel(PrototypeState state, PrototypeController controller) {
        try {
            ApiKeySettings settings = ApiKeySettings.sessionOnly("");
            return new AccessGraphPanel(state, controller, new StationMapPanel(new BundledStationRepository().load(),
                    new GoogleStaticMapsGateway(HttpClient.newHttpClient(), settings::keyForRequest), ignored -> { },
                    new BrowserLocationGateway()), settings);
        } catch (IOException exception) {
            throw new AssertionError("Could not load the test station catalogue.", exception);
        }
    }

    private static void layoutTree(Container container) {
        container.invalidate();
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container && child.isVisible()) { layoutTree((Container) child); }
        }
    }

    private static JButton button(Container root, String text) {
        return find(root, JButton.class).stream().filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button: " + text));
    }

    private static JCheckBox checkbox(Container root, String text) {
        return find(root, JCheckBox.class).stream().filter(check -> text.equals(check.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing checkbox: " + text));
    }

    private static <T extends JComponent> T named(Container root, Class<T> type, String name) {
        return find(root, type).stream().filter(component -> name.equals(
                component.getAccessibleContext().getAccessibleName())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing control: " + name));
    }

    private static <T> List<T> find(Container root, Class<T> type) {
        List<T> results = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) { results.add(type.cast(child)); }
            if (child instanceof Container) { results.addAll(find((Container) child, type)); }
        }
        return results;
    }
}
