package org.accessgraph.view;

import org.accessgraph.data_access.DisruptionAwareNetworkDataAccess;
import org.accessgraph.data_access.InMemoryDisruptionDataAccess;
import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.entity.Connection;
import org.accessgraph.entity.ServiceAlert;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.TransitNetwork;
import org.accessgraph.interface_adapter.plan_route.PlanRoutePresenter;
import org.accessgraph.interface_adapter.plan_route.PlanRouteViewModel;
import org.accessgraph.interface_adapter.reroute.JourneyRecorder;
import org.accessgraph.interface_adapter.reroute.LiveAlertsController;
import org.accessgraph.interface_adapter.reroute.LiveAlertsPresenter;
import org.accessgraph.interface_adapter.reroute.RerouteController;
import org.accessgraph.interface_adapter.reroute.ReroutePresenter;
import org.accessgraph.interface_adapter.reroute.RerouteViewModel;
import org.accessgraph.use_case.live_alerts.LiveAlertsInteractor;
import org.accessgraph.use_case.plan_route.DijkstraRouteFinder;
import org.accessgraph.use_case.plan_route.PlanRouteInputData;
import org.accessgraph.use_case.plan_route.PlanRouteInteractor;
import org.accessgraph.use_case.plan_route.ProfileDataAccessInterface;
import org.accessgraph.use_case.reroute.RerouteInteractor;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the panel through the real controllers, interactors and presenters. Live alerts come from a
 * fake gateway, so no test contacts the TTC.
 *
 * <pre>
 * Test network:  A --L1-- B --L1-- C     fastest way from A to C (4 minutes)
 *                A --L2-- D --L2-- C     slower way around (10 minutes)
 * </pre>
 */
class DisruptionPanelTest {
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
    private final PlanRouteViewModel plannerViewModel = new PlanRouteViewModel();
    private final RerouteViewModel viewModel = new RerouteViewModel();
    private final List<ServiceAlert> publishedAlerts = new ArrayList<>();
    private IOException feedFailure;
    private JourneyRecorder journeys;
    private DisruptionPanel panel;
    private JourneyTabs tabs;

    private void build() {
        List<Station> stations = new ArrayList<>();
        for (String id : List.of("A", "B", "C", "D")) {
            stations.add(new Station(id, "Station " + id, 43.6, -79.4, Station.Accessibility.CONFIRMED,
                    URI.create("https://www.ttc.ca/subway-stations/x"), LocalDate.of(2026, 11, 9)));
        }
        List<Connection> connections = new ArrayList<>();
        List<DisruptionPanel.ConnectionChoice> choices = new ArrayList<>();
        String[][] rows = {{"L1", "A", "B", "2"}, {"L1", "B", "C", "2"}, {"L2", "A", "D", "5"}, {"L2", "D", "C", "5"}};
        for (String[] row : rows) {
            connections.add(new Connection(row[1], row[2], row[0], Integer.parseInt(row[3])));
            connections.add(new Connection(row[2], row[1], row[0], Integer.parseInt(row[3])));
            choices.add(new DisruptionPanel.ConnectionChoice(row[0], row[1], row[2],
                    "Station " + row[1] + " ↔ Station " + row[2]));
        }
        TransitNetwork network = new TransitNetwork(stations, connections);
        ProfileDataAccessInterface profileAccess = () -> new AccessibilityProfile(true, 2);
        InMemoryDisruptionDataAccess disruptionAccess = new InMemoryDisruptionDataAccess();
        DijkstraRouteFinder routeFinder = new DijkstraRouteFinder();

        journeys = new JourneyRecorder(new PlanRouteInteractor(
                new DisruptionAwareNetworkDataAccess(() -> network, disruptionAccess), profileAccess, routeFinder,
                new PlanRoutePresenter(plannerViewModel)));
        RerouteInteractor reroute = new RerouteInteractor(() -> network, profileAccess, disruptionAccess,
                routeFinder, new ReroutePresenter(viewModel));
        LiveAlertsInteractor liveAlerts = new LiveAlertsInteractor(() -> network, profileAccess, () -> {
            if (feedFailure != null) {
                throw feedFailure;
            }
            return List.copyOf(publishedAlerts);
        }, disruptionAccess, routeFinder, new LiveAlertsPresenter(viewModel, ZoneOffset.UTC));
        panel = new DisruptionPanel(choices, List.of(new DisruptionPanel.TypeChoice("OUTAGE", "Service outage"),
                new DisruptionPanel.TypeChoice("CLOSURE", "Planned closure")),
                new RerouteController(reroute, journeys),
                new LiveAlertsController(liveAlerts, journeys, Clock.fixed(NOW, ZoneOffset.UTC)), viewModel);
        tabs = new JourneyTabs(new JLabel("planner"), panel, viewModel);
    }

    @Test
    void passengerSimulatesADisruptionSeesTheReplacementAndRestoresIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            assertEquals("Disruptions", tabs.getTitleAt(1));
            assertEquals(List.of("L1", "L2"), items(combo(0)));
            assertEquals(List.of("Station A ↔ Station B", "Station B ↔ Station C"), items(combo(1)));
            assertEquals(List.of("Service outage", "Planned closure"), items(combo(2)));
            assertFalse(button("Restore selected").isEnabled());

            journeys.execute(new PlanRouteInputData("A", "C"));
            assertTrue(plannerViewModel.getState().getSummary().startsWith("About 4 min"));

            combo(1).setSelectedIndex(1);
            combo(2).setSelectedIndex(1);
            button("Simulate disruption").doClick();

            String result = result().getText();
            assertTrue(result.startsWith("Simulated planned closure: L1 between Station B and Station C."), result);
            assertTrue(result.contains("Your previous route is no longer available because it used this connection."));
            assertTrue(result.contains("Replacement route: about 10 min · 0 transfers"), result);
            assertTrue(result.contains("Station A → Station D → Station C"), result);
            assertEquals(Ui.INK, result().getForeground());
            assertEquals(1, activeList().getModel().getSize());
            assertEquals("L1: Station B ↔ Station C (simulated)",
                    activeList().getModel().getElementAt(0).toString());
            assertTrue(button("Restore selected").isEnabled());
            assertEquals("Disruptions (1)", tabs.getTitleAt(1));
            assertTrue(plannerViewModel.getState().getSummary().startsWith("About 10 min"),
                    "The planner shows the recalculated route.");

            button("Simulate disruption").doClick();
            assertEquals("L1 between Station B and Station C is already disrupted.", result().getText());
            assertEquals(Ui.ERROR, result().getForeground());
            assertEquals(1, activeList().getModel().getSize());

            button("Restore selected").doClick();
            result = result().getText();
            assertTrue(result.startsWith("Restored: L1 between Station B and Station C is available again."), result);
            assertTrue(result.contains("Current route: about 4 min · 0 transfers"), result);
            assertEquals(0, activeList().getModel().getSize());
            assertFalse(button("Restore selected").isEnabled());
            assertEquals("Disruptions", tabs.getTitleAt(1));
            assertTrue(plannerViewModel.getState().getSummary().startsWith("About 4 min"));
        });
    }

    @Test
    void changingTheLineListsThatLinesConnections() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            combo(0).setSelectedItem("L2");
            assertEquals(List.of("Station A ↔ Station D", "Station D ↔ Station C"), items(combo(1)));

            button("Simulate disruption").doClick();
            assertTrue(result().getText().startsWith("Simulated service outage: L2 between Station A and Station D."));
            assertTrue(result().getText().contains("Find a route to see how this disruption affects your journey."));
        });
    }

    @Test
    void passengerLoadsLiveAlertsSeesTheClosureAndClearsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            assertTrue(hasLabel("Simulated conditions. Not live TTC data."));
            assertFalse(button("Clear").isEnabled());
            journeys.execute(new PlanRouteInputData("A", "C"));
            publishedAlerts.add(new ServiceAlert("L1", List.of("B", "C"), "Line L1: no service between B and C.",
                    NOW.minusSeconds(60), null));

            button("Load live TTC alerts").doClick();
            assertEquals("Loading live TTC alerts…", result().getText());
        });
        // The request itself runs as the next event, after the loading message has been shown.
        SwingUtilities.invokeAndWait(() -> {
            String result = result().getText();
            assertTrue(result.startsWith("Live TTC alerts loaded. 1 connection has no service."), result);
            assertTrue(result.contains("Line L1: no service between B and C."), result);
            assertTrue(result.contains("Your previous route is no longer available because it passes through a "
                    + "stretch with no service."), result);
            assertTrue(result.contains("Replacement route: about 10 min · 0 transfers"), result);
            assertEquals("L1: Station B ↔ Station C (live)", activeList().getModel().getElementAt(0).toString());
            assertTrue(hasLabel("Live TTC alerts as of 12:00."));
            assertEquals("Disruptions (1)", tabs.getTitleAt(1));
            assertTrue(button("Clear").isEnabled());
            assertTrue(plannerViewModel.getState().getSummary().startsWith("About 10 min"),
                    "The planner avoids the live closure.");

            button("Restore selected").doClick();
            assertTrue(result().getText().startsWith("L1 between Station B and Station C is closed by a live TTC "
                    + "alert"), result().getText());
            assertEquals(Ui.ERROR, result().getForeground());
            assertEquals(1, activeList().getModel().getSize());

            button("Clear").doClick();
            assertTrue(result().getText().startsWith("Live TTC alerts cleared."), result().getText());
            assertEquals(0, activeList().getModel().getSize());
            assertTrue(hasLabel("Simulated conditions. Not live TTC data."));
            assertFalse(button("Clear").isEnabled());
            assertTrue(plannerViewModel.getState().getSummary().startsWith("About 4 min"));
        });
    }

    @Test
    void failedLoadIsReportedAndKeepsSimulatedConditions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            feedFailure = new IOException("The TTC alert feed timed out. Check your connection and try again.");
            button("Load live TTC alerts").doClick();
        });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("Live TTC alerts were not loaded, so the conditions are unchanged. The TTC alert feed "
                    + "timed out. Check your connection and try again.", result().getText());
            assertEquals(Ui.ERROR, result().getForeground());
            assertTrue(hasLabel("Simulated conditions. Not live TTC data."));
            assertFalse(button("Clear").isEnabled());
        });
    }

    private boolean hasLabel(String text) {
        return find(panel, JLabel.class).stream().anyMatch(label -> text.equals(label.getText()));
    }

    private JComboBox<?> combo(int index) {
        return find(panel, JComboBox.class).get(index);
    }

    private JButton button(String label) {
        return find(panel, JButton.class).stream().filter(item -> label.equals(item.getText())).findFirst()
                .orElseThrow();
    }

    private JList<?> activeList() {
        return find(panel, JList.class).get(0);
    }

    private JTextArea result() {
        return find(panel, JTextArea.class).get(0);
    }

    private static List<String> items(JComboBox<?> combo) {
        List<String> items = new ArrayList<>();
        for (int index = 0; index < combo.getItemCount(); index++) {
            items.add(combo.getItemAt(index).toString());
        }
        return items;
    }

    private static <T> List<T> find(Container root, Class<T> type) {
        List<T> results = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                results.add(type.cast(child));
            }
            if (child instanceof Container) {
                results.addAll(find((Container) child, type));
            }
        }
        return results;
    }
}
