package org.accessgraph.interface_adapter.savedTrip;

import org.accessgraph.use_case.savedTrip.SavedTripsInputBoundary;

/** Controller for actions initiated by the Saved Trips Swing tab. */
public final class SavedTripsController {
    private final SavedTripsInputBoundary useCase;

    public SavedTripsController(SavedTripsInputBoundary useCase) {
        this.useCase = useCase;
    }

    public void list() { useCase.list(); }
    public void save(String name, String originId, String destinationId) {
        useCase.save(name, originId, destinationId);
    }
    public void load(String tripId) { useCase.load(tripId); }
    public void delete(String tripId) { useCase.delete(tripId); }
    public void refreshLoadedTrip() { useCase.refreshLoadedTrip(); }
}
