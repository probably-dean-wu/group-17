package org.accessgraph.use_case.manage_profile;

import org.accessgraph.entity.AccessibilityProfile;

import java.util.Optional;

/**
 * Restores and saves the passenger's accessibility profile. A new profile only takes effect once it has been
 * saved, so the profile searches use always matches the one that will be restored after a restart.
 */
public final class ManageProfileInteractor implements ManageProfileInputBoundary {
    private final ManageProfileDataAccessInterface profileAccess;
    private final ManageProfileOutputBoundary presenter;

    public ManageProfileInteractor(ManageProfileDataAccessInterface profileAccess,
                                   ManageProfileOutputBoundary presenter) {
        this.profileAccess = profileAccess;
        this.presenter = presenter;
    }

    @Override
    public void load() {
        Optional<AccessibilityProfile> saved;
        try {
            saved = profileAccess.readSaved();
        } catch (ProfileDataException exception) {
            presenter.prepareConfirmView(ManageProfileOutputData.of(profileAccess.getProfile()),
                    "Your saved profile could not be read: " + exception.getMessage());
            return;
        }
        if (saved.isEmpty()) {
            presenter.prepareConfirmView(ManageProfileOutputData.of(profileAccess.getProfile()),
                    "No accessibility profile has been saved yet.");
            return;
        }
        profileAccess.setProfile(saved.get());
        presenter.prepareLoadedView(ManageProfileOutputData.of(saved.get()));
    }

    @Override
    public void save(ManageProfileInputData inputData) {
        AccessibilityProfile profile;
        try {
            profile = new AccessibilityProfile(inputData.isWheelchairRequired(), inputData.isStairsAllowed(),
                    inputData.isEscalatorsAllowed(), inputData.getMaxTransfers());
        } catch (IllegalArgumentException exception) {
            presenter.prepareFailView(exception.getMessage());
            return;
        }
        try {
            profileAccess.writeSaved(profile);
        } catch (ProfileDataException exception) {
            presenter.prepareFailView("Your profile could not be saved: " + exception.getMessage()
                    + " Searches still use your previous profile.");
            return;
        }
        profileAccess.setProfile(profile);
        presenter.prepareSavedView(ManageProfileOutputData.of(profile));
    }
}
