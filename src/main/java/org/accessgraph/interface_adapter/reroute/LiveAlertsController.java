package org.accessgraph.interface_adapter.reroute;

import org.accessgraph.use_case.live_alerts.LiveAlertsInputBoundary;
import org.accessgraph.use_case.live_alerts.LiveAlertsInputData;

import java.time.Clock;

/**
 * Starts the live-alerts use case with the journey the passenger last searched for and the current
 * time, then refreshes the route planner so it shows the route under the new conditions.
 */
public final class LiveAlertsController {
    private final LiveAlertsInputBoundary interactor;
    private final JourneyRecorder journeys;
    private final Clock clock;

    public LiveAlertsController(LiveAlertsInputBoundary interactor, JourneyRecorder journeys, Clock clock) {
        this.interactor = interactor;
        this.journeys = journeys;
        this.clock = clock;
    }

    /** Loads the live alerts and applies the ones in effect now. */
    public void load() {
        interactor.load(inputData());
        journeys.searchAgain();
    }

    /** Stops applying live alerts. */
    public void clear() {
        interactor.clear(inputData());
        journeys.searchAgain();
    }

    private LiveAlertsInputData inputData() {
        return new LiveAlertsInputData(journeys.getOriginId(), journeys.getDestinationId(), clock.instant());
    }
}
