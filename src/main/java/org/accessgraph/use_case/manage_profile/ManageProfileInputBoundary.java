package org.accessgraph.use_case.manage_profile;

/** Called by the controller to restore the saved accessibility profile or to save a new one. */
public interface ManageProfileInputBoundary {
    /** Restores the profile saved on this computer, or asks the passenger to confirm one when there is none. */
    void load();

    /** Checks the passenger's choices, saves them and makes them the profile that searches use. */
    void save(ManageProfileInputData inputData);
}
