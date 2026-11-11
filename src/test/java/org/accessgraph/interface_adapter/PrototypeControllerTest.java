package org.accessgraph.interface_adapter;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.interface_adapter.PrototypeState.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrototypeControllerTest {
    private final PrototypeState state = new PrototypeState();
    private final PrototypeController controller = new PrototypeController(state);

    @Test
    void rejectsMissingIdenticalAndUnsupportedStations() {
        assertInvalidJourney("", "Union", "Select both");
        assertInvalidJourney("Union", "Union", "different");
        assertInvalidJourney("Unknown station", "Union", "sample station list");
    }

    @Test
    void reviewingARequestNeverClaimsToHaveFoundAnAccessibleRoute() {
        controller.selectJourney("Union", "Finch");
        controller.requestRoute();
        assertTrue(state.isRequestReviewed());
        assertFalse(state.isError(Screen.PLANNER));
        assertTrue(state.getNotice(Screen.PLANNER).contains("no route or accessibility assessment"));

        controller.applyProfile(new AccessibilityProfile(false, true, true, 0));
        assertFalse(state.isRequestReviewed(), "A preference change must invalidate the reviewed request.");
        assertEquals(0, state.getProfile().getMaxTransfers());

        controller.requestRoute();
        controller.setDisruption("Sample elevator connection", true);
        assertFalse(state.isRequestReviewed(), "A simulation change must invalidate the reviewed request.");
        assertEquals(1, state.getActiveDisruptionCount());
        controller.restoreConnections();
        assertEquals(0, state.getActiveDisruptionCount());
    }

    @Test
    void savedStationPairsUseCurrentPreferencesAndDisappearInANewSession() {
        controller.selectJourney("Union", "Finch");
        assertFalse(controller.saveTrip("   "));
        assertTrue(controller.saveTrip("  Campus  "));
        assertFalse(controller.saveTrip("campus"), "Duplicate names are case insensitive.");
        PrototypeState.SavedTrip saved = state.getSavedTrips().get(0);
        assertEquals("Campus", saved.getName());

        AccessibilityProfile currentProfile = new AccessibilityProfile(false, true, false, 1);
        controller.applyProfile(currentProfile);
        controller.selectJourney("Osgoode", "St Andrew");
        controller.loadTrip(saved);
        assertEquals("Union", state.getOrigin());
        assertEquals("Finch", state.getDestination());
        assertEquals(currentProfile, state.getProfile());
        assertEquals(Screen.PLANNER, state.getScreen());
        assertTrue(state.isRequestReviewed());
        assertTrue(state.getNotice(Screen.PLANNER).contains("not connected"));
        assertTrue(new PrototypeState().getSavedTrips().isEmpty());

        controller.deleteTrip(saved);
        assertTrue(state.getSavedTrips().isEmpty());
    }

    private void assertInvalidJourney(String origin, String destination, String expectedMessage) {
        controller.selectJourney(origin, destination);
        controller.requestRoute();
        assertTrue(state.isError(Screen.PLANNER));
        assertFalse(state.isRequestReviewed());
        assertTrue(state.getNotice(Screen.PLANNER).contains(expectedMessage));
        assertFalse(controller.saveTrip("Invalid journey"));
        assertTrue(state.getSavedTrips().isEmpty());
    }
}
