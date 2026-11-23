package org.accessgraph.interface_adapter.reroute;

import org.accessgraph.use_case.reroute.DisruptedConnection;
import org.accessgraph.use_case.reroute.RerouteOutputBoundary;
import org.accessgraph.use_case.reroute.RerouteOutputData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Formats the result of a disruption or a restore into text for the view model. */
public final class ReroutePresenter implements RerouteOutputBoundary {
    private static final String PREVIOUS_ROUTE_INVALID =
            "Your previous route is no longer available because it used this connection.";

    private final RerouteViewModel viewModel;

    public ReroutePresenter(RerouteViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareDisruptedView(RerouteOutputData data) {
        DisruptedConnection changed = data.getChanged();
        String headline = "Simulated " + changed.getTypeLabel().toLowerCase(Locale.ROOT) + ": " + where(changed) + ".";
        List<String> details = new ArrayList<>();
        switch (data.getOutcome()) {
            case NO_JOURNEY:
                details.add("Find a route to see how this disruption affects your journey.");
                break;
            case UNCHANGED:
                details.add("Your route does not use this connection, so it is still available.");
                addRoute(details, "Current route", data);
                break;
            case REROUTED:
                details.add(PREVIOUS_ROUTE_INVALID);
                addRoute(details, "Replacement route", data);
                break;
            case NO_ALTERNATIVE:
                details.add(PREVIOUS_ROUTE_INVALID);
                details.add("");
                details.add("No alternative route meets your accessibility profile while this disruption is active.");
                break;
            default:
                details.add("Your journey had no available route before this disruption and still has none.");
                break;
        }
        show(data, headline, details);
    }

    @Override
    public void prepareRestoredView(RerouteOutputData data) {
        String headline = "Restored: " + where(data.getChanged()) + " is available again.";
        List<String> details = new ArrayList<>();
        switch (data.getOutcome()) {
            case NO_JOURNEY:
                break;
            case UNCHANGED:
                details.add("Your route does not use this connection, so it is unchanged.");
                addRoute(details, "Current route", data);
                break;
            case REROUTED:
                details.add("Your journey can use this connection again.");
                addRoute(details, "Current route", data);
                break;
            default:
                details.add("Your journey still has no available route.");
                break;
        }
        show(data, headline, details);
    }

    /** Shows the error and keeps the active disruptions and live status, which a failed request leaves as is. */
    @Override
    public void prepareFailView(String errorMessage) {
        RerouteState previous = viewModel.getState();
        viewModel.setState(new RerouteState(previous.getActiveDisruptions(), "", List.of(), errorMessage,
                previous.getLiveStatus()));
        viewModel.firePropertyChanged();
    }

    private void show(RerouteOutputData data, String headline, List<String> details) {
        viewModel.setState(new RerouteState(DisruptionText.rows(data.getActiveDisruptions()), headline, details,
                "", viewModel.getState().getLiveStatus()));
        viewModel.firePropertyChanged();
    }

    private static String where(DisruptedConnection connection) {
        return connection.getLine() + " between " + connection.getStationName() + " and "
                + connection.getOtherStationName();
    }

    private static void addRoute(List<String> details, String title, RerouteOutputData data) {
        DisruptionText.addRoute(details, title, data.getStationNames(), data.getTotalMinutes(),
                data.getTransfers());
    }
}
