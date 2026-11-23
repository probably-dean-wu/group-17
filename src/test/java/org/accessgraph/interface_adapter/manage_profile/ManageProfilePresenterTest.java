package org.accessgraph.interface_adapter.manage_profile;

import org.accessgraph.use_case.manage_profile.ManageProfileOutputData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManageProfilePresenterTest {
    private final ManageProfileViewModel viewModel = new ManageProfileViewModel();
    private final ManageProfilePresenter presenter = new ManageProfilePresenter(viewModel);
    private int changes;

    ManageProfilePresenterTest() {
        viewModel.addPropertyChangeListener(event -> changes++);
    }

    @Test
    void loadedWheelchairProfileExplainsStepFreeRouting() {
        presenter.prepareLoadedView(new ManageProfileOutputData(true, false, false, 2, true));

        ManageProfileState state = viewModel.getState();
        assertEquals("Loaded your saved profile.", state.getStatus());
        assertEquals(List.of(
                "You board, change lines and exit only at stations the TTC confirms as accessible.",
                "Routes have at most 2 transfers."), state.getDetails());
        assertTrue(state.isWheelchairRequired());
        assertFalse(state.isNeedsConfirmation());
        assertEquals("", state.getError());
        assertEquals(1, changes);
    }

    @Test
    void savedStairsProfileSaysAnyStationCanBeUsed() {
        presenter.prepareSavedView(new ManageProfileOutputData(false, true, true, 1, false));

        ManageProfileState state = viewModel.getState();
        assertEquals("Profile saved. New searches use it.", state.getStatus());
        assertEquals(List.of(
                "Any station can be used, including stations the TTC does not confirm as step-free.",
                "Routes have at most 1 transfer."), state.getDetails());
        assertTrue(state.isStairsAllowed());
        assertEquals(1, state.getMaxTransfers());
    }

    @Test
    void escalatorsWithoutStairsExplainTheMissingData() {
        presenter.prepareSavedView(new ManageProfileOutputData(false, false, true, 0, true));

        List<String> details = viewModel.getState().getDetails();
        assertEquals(3, details.size());
        assertEquals("The station data does not include escalators yet, so allowing escalators "
                + "does not change your routes.", details.get(2));
    }

    @Test
    void confirmViewAsksThePassengerToSave() {
        presenter.prepareConfirmView(new ManageProfileOutputData(true, false, false, 2, true),
                "No accessibility profile has been saved yet.");

        ManageProfileState state = viewModel.getState();
        assertTrue(state.isNeedsConfirmation());
        assertEquals("No accessibility profile has been saved yet. Until you save one, searches use the "
                + "settings below. Check them and select Save profile.", state.getStatus());
    }

    @Test
    void failViewKeepsTheSettingsShown() {
        presenter.prepareSavedView(new ManageProfileOutputData(false, true, false, 3, false));
        presenter.prepareFailView("Your profile could not be saved: the disk is full.");

        ManageProfileState state = viewModel.getState();
        assertEquals("Your profile could not be saved: the disk is full.", state.getError());
        assertTrue(state.isStairsAllowed());
        assertEquals(3, state.getMaxTransfers());
        assertEquals("Profile saved. New searches use it.", state.getStatus());
        assertEquals(2, changes);
    }
}
