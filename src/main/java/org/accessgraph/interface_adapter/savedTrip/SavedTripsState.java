package org.accessgraph.interface_adapter.savedTrip;

import java.util.List;

/** Swing-ready state for saved trips. The view never receives domain entities. */
public final class SavedTripsState {
    public static final class TripChoice {
        private final String id;
        private final String label;

        public TripChoice(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getId() { return id; }
        @Override public String toString() { return label; }
    }

    private final List<TripChoice> trips;
    private final String selectedId;
    private final String originId;
    private final String destinationId;
    private final String comparison;
    private final String notice;
    private final String error;

    public SavedTripsState(List<TripChoice> trips, String selectedId, String originId,
                           String destinationId, String comparison, String notice, String error) {
        this.trips = List.copyOf(trips);
        this.selectedId = selectedId;
        this.originId = originId;
        this.destinationId = destinationId;
        this.comparison = comparison;
        this.notice = notice;
        this.error = error;
    }

    public static SavedTripsState empty() {
        return new SavedTripsState(List.of(), null, null, null, "", "", "");
    }

    public SavedTripsState withError(String message) {
        return new SavedTripsState(trips, selectedId, originId, destinationId, comparison, notice, message);
    }

    public List<TripChoice> getTrips() { return trips; }
    public String getSelectedId() { return selectedId; }
    public String getOriginId() { return originId; }
    public String getDestinationId() { return destinationId; }
    public String getComparison() { return comparison; }
    public String getNotice() { return notice; }
    public String getError() { return error; }
}
