package org.accessgraph.use_case.savedTrip;

import org.accessgraph.entity.SavedTrip;
import org.accessgraph.entity.RouteSnapshot;
import java.util.List;

/** Unformatted use-case results; the presenter prepares user-facing text. */
public final class SavedTripsOutputData {
    private final List<SavedTrip> trips;
    private final SavedTrip selected;
    private final RouteSnapshot currentRoute;
    private final List<String> previousStationNames;
    private final List<String> currentStationNames;
    private final boolean previousFeasible;
    private final String notice;

    public SavedTripsOutputData(List<SavedTrip> trips, SavedTrip selected, RouteSnapshot currentRoute,
                                List<String> previousStationNames, List<String> currentStationNames,
                                boolean previousFeasible, String notice) {
        this.trips = List.copyOf(trips);
        this.selected = selected;
        this.currentRoute = currentRoute;
        this.previousStationNames = List.copyOf(previousStationNames);
        this.currentStationNames = List.copyOf(currentStationNames);
        this.previousFeasible = previousFeasible;
        this.notice = notice;
    }

    public List<SavedTrip> getTrips() { return trips; }
    public SavedTrip getSelected() { return selected; }
    public RouteSnapshot getCurrentRoute() { return currentRoute; }
    public List<String> getPreviousStationNames() { return previousStationNames; }
    public List<String> getCurrentStationNames() { return currentStationNames; }
    public boolean isPreviousFeasible() { return previousFeasible; }
    public String getNotice() { return notice; }
}
