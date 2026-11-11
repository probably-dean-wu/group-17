package org.accessgraph.interface_adapter;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.interface_adapter.PrototypeState.SavedTrip;
import org.accessgraph.interface_adapter.PrototypeState.Screen;

/** Coordinates prototype UI actions only. Future use-case interactors belong behind this adapter. */
public final class PrototypeController {
    private final PrototypeState state;

    public PrototypeController(PrototypeState state) { this.state = state; }

    public void navigate(Screen screen) {
        state.setScreen(screen);
        state.changed();
    }

    public void selectJourney(String origin, String destination) {
        state.setJourney(origin == null ? "" : origin, destination == null ? "" : destination);
        invalidateJourney(PrototypeState.DEFAULT_PLANNER_NOTICE);
        state.changed();
    }

    public void swapStations() { selectJourney(state.getDestination(), state.getOrigin()); }

    public void requestRoute() {
        state.setRequestReviewed(false);
        String error = journeyError();
        if (error != null) {
            state.notice(Screen.PLANNER, error, true);
        } else {
            state.setRequestReviewed(true);
            state.notice(Screen.PLANNER,
                    "Journey request reviewed. Route search is not connected yet; "
                            + "no route or accessibility assessment is available.", false);
        }
        state.changed();
    }

    public void applyProfile(AccessibilityProfile profile) {
        state.setProfile(profile);
        state.notice(Screen.PROFILE, "Preferences applied for this session. They reset when you close AccessGraph.",
                false);
        invalidateJourney("Preferences changed. Review your journey again with Find route.");
        state.changed();
    }

    public void resetProfile() { applyProfile(AccessibilityProfile.defaults()); }

    public void setDisruption(String connection, boolean unavailable) {
        if (!state.getDisruptions().containsKey(connection)) {
            throw new IllegalArgumentException("Unknown sample connection.");
        }
        state.setDisruption(connection, unavailable);
        state.notice(Screen.DISRUPTIONS,
                connection + (unavailable ? " marked unavailable." : " restored.")
                        + " This changes the simulation display only; rerouting is not connected.", false);
        invalidateJourney("Simulation changed. Route search and comparison are not connected yet.");
        state.changed();
    }

    public void restoreConnections() {
        for (String connection : state.getDisruptions().keySet()) {
            state.setDisruption(connection, false);
        }
        state.notice(Screen.DISRUPTIONS, "All sample connections restored. No live TTC conditions were changed.",
                false);
        invalidateJourney("Simulation restored. Review your journey again with Find route.");
        state.changed();
    }

    public boolean saveTrip(String name) {
        String error = journeyError();
        String trimmed = name == null ? "" : name.trim();
        if (error == null && (trimmed.isEmpty() || trimmed.length() > 48)) {
            error = "Enter a trip name between 1 and 48 characters.";
        }
        if (error == null && state.getSavedTrips().stream()
                .anyMatch(trip -> trip.getName().equalsIgnoreCase(trimmed))) {
            error = "That trip name is already in use. Choose a different name.";
        }
        if (error != null) {
            state.notice(Screen.PLANNER, error, true);
            state.changed();
            return false;
        }
        state.addTrip(new SavedTrip(trimmed, state.getOrigin(), state.getDestination()));
        state.notice(Screen.PLANNER, "Saved “" + trimmed + "” for this session. View it in Saved trips.", false);
        state.notice(Screen.SAVED_TRIPS, "Trips are kept for this session only. Loading reviews the station pair "
                + "with your current preferences; route search is not connected.", false);
        state.changed();
        return true;
    }

    public void loadTrip(SavedTrip trip) {
        if (!state.getSavedTrips().contains(trip)) { return; }
        state.setJourney(trip.getOrigin(), trip.getDestination());
        state.setScreen(Screen.PLANNER);
        requestRoute();
    }

    public void deleteTrip(SavedTrip trip) {
        if (state.getSavedTrips().contains(trip)) {
            state.removeTrip(trip);
            state.notice(Screen.SAVED_TRIPS, "Deleted “" + trip.getName() + "” from this session.", false);
            state.changed();
        }
    }

    private String journeyError() {
        if (state.getOrigin().isEmpty() || state.getDestination().isEmpty()) {
            return "Select both an origin and a destination.";
        }
        if (!PrototypeState.STATIONS.contains(state.getOrigin())
                || !PrototypeState.STATIONS.contains(state.getDestination())) {
            return "Choose stations from the sample station list.";
        }
        if (state.getOrigin().equals(state.getDestination())) {
            return "Choose a destination different from your origin.";
        }
        return null;
    }

    private void invalidateJourney(String message) {
        state.setRequestReviewed(false);
        state.notice(Screen.PLANNER, message, false);
    }
}
