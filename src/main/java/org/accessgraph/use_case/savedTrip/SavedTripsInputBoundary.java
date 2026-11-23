package org.accessgraph.use_case.savedTrip;

/** User operations for the saved-trips use case, plus a disruption notification. */
public interface SavedTripsInputBoundary {
    void list();
    void save(String name, String originId, String destinationId);
    void load(String tripId);
    void delete(String tripId);
    void refreshLoadedTrip();
}
