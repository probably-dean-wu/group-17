package org.accessgraph.interface_adapter.manage_profile;

import org.accessgraph.interface_adapter.reroute.JourneyRecorder;
import org.accessgraph.use_case.manage_profile.ManageProfileInputBoundary;
import org.accessgraph.use_case.manage_profile.ManageProfileInputData;

/**
 * Turns the view's settings into input data and starts the profile use case. After a save, the journey the
 * passenger last searched for is planned again, so the route shown matches the profile in use.
 */
public final class ManageProfileController {
    private final ManageProfileInputBoundary interactor;
    private final JourneyRecorder journeys;

    public ManageProfileController(ManageProfileInputBoundary interactor, JourneyRecorder journeys) {
        this.interactor = interactor;
        this.journeys = journeys;
    }

    /** Restores the saved profile. Called once when the application starts, before any search. */
    public void load() {
        interactor.load();
    }

    public void save(boolean wheelchairRequired, boolean stairsAllowed, boolean escalatorsAllowed,
                     int maxTransfers) {
        interactor.save(new ManageProfileInputData(wheelchairRequired, stairsAllowed, escalatorsAllowed,
                maxTransfers));
        journeys.searchAgain();
    }
}
