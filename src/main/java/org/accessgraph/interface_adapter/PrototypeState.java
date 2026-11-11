package org.accessgraph.interface_adapter;

import org.accessgraph.entity.AccessibilityProfile;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Session-only presentation state. No file storage, transit graph, or live data. */
public final class PrototypeState {
    public enum Screen { STATION_MAP, PLANNER, PROFILE, DISRUPTIONS, SAVED_TRIPS }

    public static final List<String> STATIONS = List.of(
            "Bloor–Yonge", "Eglinton", "Finch", "Osgoode",
            "Queen's Park", "St Andrew", "St George", "Union");
    public static final String DEFAULT_PLANNER_NOTICE =
            "Choose your stations and select Find route to review the journey request.";

    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private final Map<Screen, String> notices = new EnumMap<>(Screen.class);
    private final Map<Screen, Boolean> errors = new EnumMap<>(Screen.class);
    private final List<SavedTrip> savedTrips = new ArrayList<>();
    private final Map<String, Boolean> disruptions = new LinkedHashMap<>();
    private String origin = "";
    private String destination = "";
    private AccessibilityProfile profile = AccessibilityProfile.defaults();
    private Screen screen = Screen.STATION_MAP;
    private boolean requestReviewed;

    public PrototypeState() {
        notices.put(Screen.PLANNER, DEFAULT_PLANNER_NOTICE);
        notices.put(Screen.PROFILE, "Preferences apply to this session only.");
        notices.put(Screen.DISRUPTIONS, "All sample connections are available in this simulation.");
        notices.put(Screen.SAVED_TRIPS, "Save a station pair from the route planner to get started.");
        disruptions.put("Sample elevator connection", false);
        disruptions.put("Sample platform transfer", false);
    }

    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public AccessibilityProfile getProfile() { return profile; }
    public Screen getScreen() { return screen; }
    public boolean isRequestReviewed() { return requestReviewed; }
    public String getNotice(Screen area) { return notices.get(area); }
    public boolean isError(Screen area) { return errors.getOrDefault(area, false); }
    public List<SavedTrip> getSavedTrips() { return List.copyOf(savedTrips); }
    public Map<String, Boolean> getDisruptions() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(disruptions));
    }
    public long getActiveDisruptionCount() {
        return disruptions.values().stream().filter(Boolean::booleanValue).count();
    }

    public void addChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    void setJourney(String from, String to) { origin = from; destination = to; }
    void setProfile(AccessibilityProfile value) { profile = value; }
    void setScreen(Screen value) { screen = value; }
    void setRequestReviewed(boolean value) { requestReviewed = value; }
    void addTrip(SavedTrip trip) { savedTrips.add(trip); }
    void removeTrip(SavedTrip trip) { savedTrips.remove(trip); }
    void setDisruption(String connection, boolean unavailable) {
        disruptions.put(connection, unavailable);
    }
    void notice(Screen area, String text, boolean error) {
        notices.put(area, text);
        errors.put(area, error);
    }
    void changed() { changes.firePropertyChange("state", null, this); }

    /** An origin–destination shortcut, never a stored or verified route. */
    public static final class SavedTrip {
        private final String name;
        private final String origin;
        private final String destination;

        SavedTrip(String name, String origin, String destination) {
            this.name = name;
            this.origin = origin;
            this.destination = destination;
        }

        public String getName() { return name; }
        public String getOrigin() { return origin; }
        public String getDestination() { return destination; }
    }
}
