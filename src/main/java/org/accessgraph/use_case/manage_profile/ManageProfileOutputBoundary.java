package org.accessgraph.use_case.manage_profile;

/** Implemented by the presenter; the interactor reports its result through this interface. */
public interface ManageProfileOutputBoundary {
    /** The saved profile was restored and searches use it. */
    void prepareLoadedView(ManageProfileOutputData outputData);

    /** The passenger's new profile was saved and searches use it from now on. */
    void prepareSavedView(ManageProfileOutputData outputData);

    /**
     * No usable profile was saved, so searches use the profile shown until the passenger confirms one.
     *
     * @param reason why the saved profile could not be restored
     */
    void prepareConfirmView(ManageProfileOutputData outputData, String reason);

    /** Nothing changed because the choices were invalid or could not be saved. */
    void prepareFailView(String errorMessage);
}
