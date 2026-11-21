package org.accessgraph.use_case.manage_profile;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.use_case.plan_route.ProfileDataAccessInterface;

import java.util.Optional;

/**
 * Reads and writes the saved accessibility profile, and holds the profile that searches use. Implemented
 * in the data access layer. The route planner only sees the narrower {@link ProfileDataAccessInterface}.
 */
public interface ManageProfileDataAccessInterface extends ProfileDataAccessInterface {
    /**
     * Reads the profile saved on this computer.
     *
     * @return the saved profile, or empty when none has been saved yet
     * @throws ProfileDataException when the saved profile cannot be read or is not valid
     */
    Optional<AccessibilityProfile> readSaved() throws ProfileDataException;

    /**
     * Saves the profile so it is restored the next time the application starts.
     *
     * @throws ProfileDataException when the profile cannot be saved
     */
    void writeSaved(AccessibilityProfile profile) throws ProfileDataException;

    /** Makes the profile the one that route searches use from now on. */
    void setProfile(AccessibilityProfile profile);
}
