package org.accessgraph.use_case.live_alerts;

/** Implemented by the presenter; the interactor reports its result through this interface. */
public interface LiveAlertsOutputBoundary {
    /** The live alerts were retrieved and applied, and the journey was recalculated. */
    void prepareLoadedView(LiveAlertsOutputData outputData);

    /** Live alerts no longer apply, and the journey was recalculated. */
    void prepareClearedView(LiveAlertsOutputData outputData);

    /** Nothing changed because the alerts could not be retrieved. */
    void prepareFailView(String errorMessage);
}
