package org.accessgraph.use_case.live_alerts;

/** Called by the controller to bring the transit agency's live alerts into the routing conditions. */
public interface LiveAlertsInputBoundary {
    /** Retrieves the live alerts, closes the connections they leave without service, and recalculates. */
    void load(LiveAlertsInputData inputData);

    /** Stops applying live alerts, so only simulated disruptions remain, and recalculates. */
    void clear(LiveAlertsInputData inputData);
}
