package org.accessgraph.interface_adapter.savedTrip;

import org.accessgraph.entity.RouteSnapshot;
import org.accessgraph.entity.SavedTrip;
import org.accessgraph.use_case.savedTrip.SavedTripsOutputBoundary;
import org.accessgraph.use_case.savedTrip.SavedTripsOutputData;

import java.util.ArrayList;
import java.util.List;

/** Translates route-comparison results to plain Swing text. */
public final class SavedTripsPresenter implements SavedTripsOutputBoundary {
    private final SavedTripsViewModel viewModel;

    public SavedTripsPresenter(SavedTripsViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareSuccessView(SavedTripsOutputData data) {
        List<SavedTripsState.TripChoice> choices = new ArrayList<>();
        for (SavedTrip trip : data.getTrips()) {
            choices.add(new SavedTripsState.TripChoice(trip.getId(), trip.getName() + " ("
                    + trip.getOriginId() + " → " + trip.getDestinationId() + ")"));
        }
        SavedTrip selected = data.getSelected();
        String details = selected == null ? "Select a trip, then choose Load / compare." : formatComparison(data);
        viewModel.setState(new SavedTripsState(choices, selected == null ? null : selected.getId(),
                selected == null ? null : selected.getOriginId(),
                selected == null ? null : selected.getDestinationId(), details, data.getNotice(), ""));
        viewModel.firePropertyChanged();
    }

    @Override
    public void prepareFailView(String message) {
        viewModel.setState(viewModel.getState().withError(message));
        viewModel.firePropertyChanged();
    }

    private static String formatComparison(SavedTripsOutputData data) {
        RouteSnapshot old = data.getSelected().getPreviousRoute();
        RouteSnapshot now = data.getCurrentRoute();
        List<String> details = new ArrayList<>();
        details.add("Trip: " + data.getSelected().getName());
        details.add("");
        if (old == null) {
            details.add("SAVED ROUTE: No route was available when this trip was saved.");
        } else {
            details.add("SAVED ROUTE — " + (data.isPreviousFeasible()
                    ? "STILL FEASIBLE" : "NO LONGER FEASIBLE"));
            details.add(routeSummary(old, data.getPreviousStationNames()));
        }
        details.add("");
        details.add("CURRENT ROUTE — recalculated now");
        if (now == null) {
            details.add("No feasible route with the current profile and disruptions.");
        } else {
            details.add(routeSummary(now, data.getCurrentStationNames()));
        }
        if (old != null && now != null) {
            details.add("");
            int delta = now.getTotalMinutes() - old.getTotalMinutes();
            int transferDelta = now.getTransfers() - old.getTransfers();
            details.add("Difference: " + signed(delta) + " min, " + signed(transferDelta) + " transfers");
            boolean same = old.getStationIds().equals(now.getStationIds())
                    && old.getLegLines().equals(now.getLegLines());
            details.add(same ? "Station sequence and lines are unchanged."
                    : "Station sequence or lines have changed.");
        }
        return String.join("\n", details);
    }

    private static String routeSummary(RouteSnapshot route, List<String> names) {
        return "About " + route.getTotalMinutes() + " min · " + route.getTransfers()
                + (route.getTransfers() == 1 ? " transfer" : " transfers")
                + "\nStations: " + String.join(" → ", names)
                + "\nLines: " + String.join(" → ", route.getLegLines());
    }

    private static String signed(int amount) {
        return amount > 0 ? "+" + amount : Integer.toString(amount);
    }
}
