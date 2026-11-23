package org.accessgraph.interface_adapter.reroute;

import org.accessgraph.use_case.live_alerts.LiveAlertsOutputBoundary;
import org.accessgraph.use_case.live_alerts.LiveAlertsOutputData;
import org.accessgraph.use_case.reroute.DisruptedConnection;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Formats the result of loading or clearing live alerts into text for the disruption view's model. */
public final class LiveAlertsPresenter implements LiveAlertsOutputBoundary {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String PREVIOUS_ROUTE_CLOSED =
            "Your previous route is no longer available because it passes through a stretch with no service.";

    private final RerouteViewModel viewModel;
    private final ZoneId zone;

    /**
     * @param zone the time zone used to show when the alerts were loaded
     */
    public LiveAlertsPresenter(RerouteViewModel viewModel, ZoneId zone) {
        this.viewModel = viewModel;
        this.zone = zone;
    }

    @Override
    public void prepareLoadedView(LiveAlertsOutputData data) {
        long closed = data.getActiveDisruptions().stream().filter(DisruptedConnection::isLive).count();
        String headline;
        if (closed == 0) {
            headline = "Live TTC alerts loaded. No supported connection is closed right now.";
        } else {
            headline = "Live TTC alerts loaded. " + closed
                    + (closed == 1 ? " connection has" : " connections have") + " no service.";
        }
        List<String> details = new ArrayList<>();
        for (String alert : data.getAppliedAlerts()) {
            addParagraph(details, alert);
        }
        if (!data.getUnmatchedAlerts().isEmpty()) {
            addParagraph(details, "Not applied, because it matches no supported connection:");
            details.addAll(data.getUnmatchedAlerts());
        }
        switch (data.getOutcome()) {
            case NO_JOURNEY:
                if (closed > 0) {
                    addParagraph(details, "Find a route to plan around the closed connections.");
                }
                break;
            case UNCHANGED:
                if (closed > 0) {
                    addParagraph(details, "Your route avoids every closed connection.");
                }
                addRoute(details, "Current route", data);
                break;
            case REROUTED:
                addParagraph(details, PREVIOUS_ROUTE_CLOSED);
                addRoute(details, "Replacement route", data);
                break;
            case NO_ALTERNATIVE:
                addParagraph(details, PREVIOUS_ROUTE_CLOSED);
                addParagraph(details,
                        "No alternative route meets your accessibility profile while the alert is in effect.");
                break;
            default:
                addParagraph(details, "Your journey still has no available route.");
                break;
        }
        show(data, headline, details, "Live TTC alerts as of " + TIME.format(data.getCheckedAt().atZone(zone)) + ".");
    }

    @Override
    public void prepareClearedView(LiveAlertsOutputData data) {
        List<String> details = new ArrayList<>();
        switch (data.getOutcome()) {
            case NO_JOURNEY:
                break;
            case UNCHANGED:
                addParagraph(details, "Your route is unchanged.");
                addRoute(details, "Current route", data);
                break;
            case REROUTED:
                addParagraph(details, "Your journey can use the reopened connections again.");
                addRoute(details, "Current route", data);
                break;
            default:
                addParagraph(details, "Your journey still has no available route.");
                break;
        }
        show(data, "Live TTC alerts cleared. Only simulated disruptions apply now.", details, "");
    }

    /** Shows the error and keeps the active disruptions and live status, which a failed load leaves as is. */
    @Override
    public void prepareFailView(String errorMessage) {
        RerouteState previous = viewModel.getState();
        viewModel.setState(new RerouteState(previous.getActiveDisruptions(), "", List.of(), errorMessage,
                previous.getLiveStatus()));
        viewModel.firePropertyChanged();
    }

    private void show(LiveAlertsOutputData data, String headline, List<String> details, String liveStatus) {
        viewModel.setState(new RerouteState(DisruptionText.rows(data.getActiveDisruptions()), headline, details,
                "", liveStatus));
        viewModel.firePropertyChanged();
    }

    /** Adds the text as its own paragraph, separated from what is already there by a blank line. */
    private static void addParagraph(List<String> details, String text) {
        if (!details.isEmpty()) {
            details.add("");
        }
        details.add(text);
    }

    private static void addRoute(List<String> details, String title, LiveAlertsOutputData data) {
        DisruptionText.addRoute(details, title, data.getStationNames(), data.getTotalMinutes(),
                data.getTransfers());
    }
}
