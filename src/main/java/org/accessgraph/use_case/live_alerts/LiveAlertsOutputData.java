package org.accessgraph.use_case.live_alerts;

import org.accessgraph.use_case.reroute.DisruptedConnection;
import org.accessgraph.use_case.reroute.RerouteOutputData.Outcome;

import java.time.Instant;
import java.util.List;

/**
 * Which live alerts apply and what they did to the passenger's journey, as plain data the presenter can
 * format.
 */
public final class LiveAlertsOutputData {
    private final Instant checkedAt;
    private final List<String> appliedAlerts;
    private final List<String> unmatchedAlerts;
    private final List<DisruptedConnection> activeDisruptions;
    private final Outcome outcome;
    private final List<String> stationNames;
    private final int totalMinutes;
    private final int transfers;

    /**
     * @param checkedAt the moment the alerts were judged against
     * @param appliedAlerts the wording of each alert in effect that closed a supported connection
     * @param unmatchedAlerts the wording of each alert in effect that matched no supported connection
     * @param activeDisruptions every disruption that is active after the change, live or simulated
     * @param outcome how the change affected the journey
     * @param stationNames the stations of the journey's current route in order, empty when there is none
     * @param totalMinutes the estimated duration of the current route
     * @param transfers the number of line changes on the current route
     */
    public LiveAlertsOutputData(Instant checkedAt, List<String> appliedAlerts, List<String> unmatchedAlerts,
                                List<DisruptedConnection> activeDisruptions, Outcome outcome,
                                List<String> stationNames, int totalMinutes, int transfers) {
        this.checkedAt = checkedAt;
        this.appliedAlerts = List.copyOf(appliedAlerts);
        this.unmatchedAlerts = List.copyOf(unmatchedAlerts);
        this.activeDisruptions = List.copyOf(activeDisruptions);
        this.outcome = outcome;
        this.stationNames = List.copyOf(stationNames);
        this.totalMinutes = totalMinutes;
        this.transfers = transfers;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public List<String> getAppliedAlerts() {
        return appliedAlerts;
    }

    public List<String> getUnmatchedAlerts() {
        return unmatchedAlerts;
    }

    public List<DisruptedConnection> getActiveDisruptions() {
        return activeDisruptions;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public List<String> getStationNames() {
        return stationNames;
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }

    public int getTransfers() {
        return transfers;
    }
}
