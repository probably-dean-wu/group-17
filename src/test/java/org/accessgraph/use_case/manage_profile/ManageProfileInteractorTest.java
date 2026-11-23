package org.accessgraph.use_case.manage_profile;

import org.accessgraph.entity.AccessibilityProfile;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The interactor is tested with fake data access and a fake presenter: no Swing, no files. */
class ManageProfileInteractorTest {
    private static final AccessibilityProfile STAIRS_OK = new AccessibilityProfile(false, true, true, 1);

    /** Remembers what was saved in memory, and can be told to fail like an unreadable or full disk. */
    private static final class FakeProfileAccess implements ManageProfileDataAccessInterface {
        private AccessibilityProfile current = AccessibilityProfile.strictest();
        private AccessibilityProfile saved;
        private ProfileDataException readFailure;
        private ProfileDataException writeFailure;

        @Override
        public AccessibilityProfile getProfile() {
            return current;
        }

        @Override
        public void setProfile(AccessibilityProfile profile) {
            current = profile;
        }

        @Override
        public Optional<AccessibilityProfile> readSaved() throws ProfileDataException {
            if (readFailure != null) {
                throw readFailure;
            }
            return Optional.ofNullable(saved);
        }

        @Override
        public void writeSaved(AccessibilityProfile profile) throws ProfileDataException {
            if (writeFailure != null) {
                throw writeFailure;
            }
            saved = profile;
        }
    }

    private static final class FakePresenter implements ManageProfileOutputBoundary {
        private ManageProfileOutputData loaded;
        private ManageProfileOutputData savedView;
        private ManageProfileOutputData confirm;
        private String reason;
        private String error;

        @Override
        public void prepareLoadedView(ManageProfileOutputData outputData) {
            loaded = outputData;
        }

        @Override
        public void prepareSavedView(ManageProfileOutputData outputData) {
            savedView = outputData;
        }

        @Override
        public void prepareConfirmView(ManageProfileOutputData outputData, String reason) {
            confirm = outputData;
            this.reason = reason;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }

    private final FakeProfileAccess access = new FakeProfileAccess();
    private final FakePresenter presenter = new FakePresenter();
    private final ManageProfileInteractor interactor = new ManageProfileInteractor(access, presenter);

    @Test
    void loadRestoresTheSavedProfileForSearches() {
        access.saved = STAIRS_OK;
        interactor.load();

        assertSame(STAIRS_OK, access.getProfile());
        assertTrue(presenter.loaded.isStairsAllowed());
        assertEquals(1, presenter.loaded.getMaxTransfers());
        assertFalse(presenter.loaded.isStepFreeRouting());
        assertNull(presenter.confirm);
    }

    @Test
    void loadWithNothingSavedAsksToConfirmTheStrictestProfile() {
        interactor.load();

        assertEquals("No accessibility profile has been saved yet.", presenter.reason);
        assertTrue(presenter.confirm.isWheelchairRequired());
        assertTrue(presenter.confirm.isStepFreeRouting());
        assertEquals(2, presenter.confirm.getMaxTransfers());
        assertTrue(access.getProfile().isWheelchairRequired());
        assertNull(presenter.loaded);
    }

    @Test
    void loadWithUnreadableDataReportsWhyAndKeepsTheStrictestProfile() {
        access.readFailure = new ProfileDataException("the setting \"maxTransfers\" is \"abc\".");
        interactor.load();

        assertEquals("Your saved profile could not be read: the setting \"maxTransfers\" is \"abc\".",
                presenter.reason);
        assertTrue(presenter.confirm.isWheelchairRequired());
        assertTrue(access.getProfile().isWheelchairRequired());
    }

    @Test
    void saveWritesTheProfileAndSearchesUseIt() {
        interactor.save(new ManageProfileInputData(false, true, false, 0));

        assertTrue(access.saved.isStairsAllowed());
        assertEquals(0, access.saved.getMaxTransfers());
        assertSame(access.saved, access.getProfile());
        assertTrue(presenter.savedView.isStairsAllowed());
        assertFalse(presenter.savedView.isEscalatorsAllowed());
        assertNull(presenter.error);
    }

    @Test
    void invalidChoicesChangeNothing() {
        interactor.save(new ManageProfileInputData(true, true, false, 2));

        assertEquals("A wheelchair profile cannot allow stairs or escalators.", presenter.error);
        assertNull(access.saved);
        assertTrue(access.getProfile().isWheelchairRequired());
        assertNull(presenter.savedView);
    }

    @Test
    void transferLimitOutOfRangeIsRejected() {
        interactor.save(new ManageProfileInputData(false, true, true, AccessibilityProfile.MAX_TRANSFERS + 1));

        assertEquals("Maximum transfers cannot be more than " + AccessibilityProfile.MAX_TRANSFERS + ".",
                presenter.error);
        assertNull(access.saved);
    }

    @Test
    void aFailedSaveKeepsThePreviousProfileInUse() {
        access.writeFailure = new ProfileDataException("the disk is full.");
        interactor.save(new ManageProfileInputData(false, true, true, 1));

        assertEquals("Your profile could not be saved: the disk is full. Searches still use your previous profile.",
                presenter.error);
        assertTrue(access.getProfile().isWheelchairRequired());
        assertNull(presenter.savedView);
    }
}
