package org.accessgraph.use_case.savedTrip;

/** Sends use-case results to a presenter, never directly to Swing. */
public interface SavedTripsOutputBoundary {
    void prepareSuccessView(SavedTripsOutputData data);
    void prepareFailView(String message);
}
