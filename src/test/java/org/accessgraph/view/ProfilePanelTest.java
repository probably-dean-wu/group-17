package org.accessgraph.view;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.entity.Connection;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.TransitNetwork;
import org.accessgraph.interface_adapter.manage_profile.ManageProfileController;
import org.accessgraph.interface_adapter.manage_profile.ManageProfilePresenter;
import org.accessgraph.interface_adapter.manage_profile.ManageProfileViewModel;
import org.accessgraph.interface_adapter.plan_route.PlanRoutePresenter;
import org.accessgraph.interface_adapter.plan_route.PlanRouteViewModel;
import org.accessgraph.interface_adapter.reroute.JourneyRecorder;
import org.accessgraph.interface_adapter.reroute.RerouteViewModel;
import org.accessgraph.use_case.manage_profile.ManageProfileDataAccessInterface;
import org.accessgraph.use_case.manage_profile.ManageProfileInteractor;
import org.accessgraph.use_case.plan_route.DijkstraRouteFinder;
import org.accessgraph.use_case.plan_route.PlanRouteInputData;
import org.accessgraph.use_case.plan_route.PlanRouteInteractor;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the panel through the real controller, interactor and presenter, with the saved profile kept in
 * memory. The test network is King (not confirmed accessible) to Union (confirmed), one stop on Line 1.
 */
class ProfilePanelTest {
    /** Keeps the "saved" profile in memory instead of a file. */
    private static final class MemoryProfileAccess implements ManageProfileDataAccessInterface {
        private AccessibilityProfile current = AccessibilityProfile.strictest();
        private AccessibilityProfile saved;

        @Override
        public AccessibilityProfile getProfile() {
            return current;
        }

        @Override
        public void setProfile(AccessibilityProfile profile) {
            current = profile;
        }

        @Override
        public Optional<AccessibilityProfile> readSaved() {
            return Optional.ofNullable(saved);
        }

        @Override
        public void writeSaved(AccessibilityProfile profile) {
            saved = profile;
        }
    }

    private final MemoryProfileAccess access = new MemoryProfileAccess();
    private final PlanRouteViewModel plannerViewModel = new PlanRouteViewModel();
    private JourneyRecorder journeys;
    private ManageProfileController controller;
    private ProfilePanel panel;
    private JourneyTabs tabs;

    private void build() {
        Station union = new Station("1", "Union", 43.6, -79.4, Station.Accessibility.CONFIRMED,
                URI.create("https://www.ttc.ca/x"), LocalDate.of(2026, 11, 9));
        Station king = new Station("2", "King", 43.6, -79.4, Station.Accessibility.LIMITED,
                URI.create("https://www.ttc.ca/x"), LocalDate.of(2026, 11, 9));
        TransitNetwork network = new TransitNetwork(List.of(union, king), List.of(
                new Connection("2", "1", "Line 1", 2), new Connection("1", "2", "Line 1", 2)));
        journeys = new JourneyRecorder(new PlanRouteInteractor(() -> network, access, new DijkstraRouteFinder(),
                new PlanRoutePresenter(plannerViewModel)));

        ManageProfileViewModel viewModel = new ManageProfileViewModel();
        controller = new ManageProfileController(
                new ManageProfileInteractor(access, new ManageProfilePresenter(viewModel)), journeys);
        panel = new ProfilePanel(controller, viewModel, AccessibilityProfile.MAX_TRANSFERS);
        tabs = new JourneyTabs(new JLabel("planner"), new JLabel("disruptions"), new RerouteViewModel());
        tabs.addProfileTab(panel, viewModel);
    }

    @Test
    void firstStartOpensTheProfileTabAndSavingChangesTheRoute() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            assertEquals("Profile", tabs.getTitleAt(2));
            assertEquals(0, tabs.getSelectedIndex());

            controller.load();
            assertEquals(2, tabs.getSelectedIndex(), "With nothing saved, the passenger is asked to confirm.");
            assertTrue(message().getText().startsWith("No accessibility profile has been saved yet."),
                    message().getText());
            assertTrue(box("I use a wheelchair (elevators and ramps only)").isSelected());
            assertFalse(box("Stairs are OK").isEnabled());
            assertEquals(2, transfers().getSelectedItem());
            assertEquals(List.of(0, 1, 2, 3), items(transfers()));

            journeys.execute(new PlanRouteInputData("2", "1"));
            assertTrue(plannerViewModel.getState().getError().startsWith("King is not confirmed as accessible"));

            box("I use a wheelchair (elevators and ramps only)").doClick();
            assertTrue(box("Stairs are OK").isEnabled());
            box("Stairs are OK").doClick();
            transfers().setSelectedItem(1);
            button("Save profile").doClick();

            assertTrue(message().getText().startsWith("Profile saved. New searches use it."), message().getText());
            assertTrue(message().getText().contains("Routes have at most 1 transfer."));
            assertEquals(Ui.INK, message().getForeground());
            assertTrue(access.saved.isStairsAllowed());
            assertEquals(1, access.saved.getMaxTransfers());
            assertTrue(plannerViewModel.getState().getSummary().startsWith("About 2 min"),
                    "The route shown is planned again with the new profile.");
        });
    }

    @Test
    void savedProfileIsShownWithoutSwitchingTabs() throws Exception {
        access.saved = new AccessibilityProfile(false, false, true, 0);
        SwingUtilities.invokeAndWait(() -> {
            build();
            controller.load();
            assertEquals(0, tabs.getSelectedIndex());
            assertFalse(box("I use a wheelchair (elevators and ramps only)").isSelected());
            assertFalse(box("Stairs are OK").isSelected());
            assertTrue(box("Escalators are OK").isSelected());
            assertEquals(0, transfers().getSelectedItem());
            assertTrue(message().getText().startsWith("Loaded your saved profile."));
            assertTrue(message().getText().contains("does not include escalators yet"));
        });
    }

    @Test
    void choosingWheelchairClearsAndDisablesStairsAndEscalators() throws Exception {
        access.saved = new AccessibilityProfile(false, true, true, 2);
        SwingUtilities.invokeAndWait(() -> {
            build();
            controller.load();
            assertTrue(box("Stairs are OK").isSelected());

            box("I use a wheelchair (elevators and ramps only)").doClick();
            assertFalse(box("Stairs are OK").isSelected());
            assertFalse(box("Stairs are OK").isEnabled());
            assertFalse(box("Escalators are OK").isSelected());
            assertFalse(box("Escalators are OK").isEnabled());

            button("Save profile").doClick();
            assertTrue(access.saved.isWheelchairRequired());
        });
    }

    private JCheckBox box(String label) {
        return find(panel, JCheckBox.class).stream().filter(item -> label.equals(item.getText())).findFirst()
                .orElseThrow();
    }

    private JButton button(String label) {
        return find(panel, JButton.class).stream().filter(item -> label.equals(item.getText())).findFirst()
                .orElseThrow();
    }

    private JComboBox<?> transfers() {
        return find(panel, JComboBox.class).get(0);
    }

    private JTextArea message() {
        return find(panel, JTextArea.class).get(0);
    }

    private static List<Object> items(JComboBox<?> combo) {
        List<Object> items = new ArrayList<>();
        for (int index = 0; index < combo.getItemCount(); index++) {
            items.add(combo.getItemAt(index));
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
