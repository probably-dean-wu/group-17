package org.accessgraph.interface_adapter.manage_profile;

import org.accessgraph.use_case.manage_profile.ManageProfileOutputBoundary;
import org.accessgraph.use_case.manage_profile.ManageProfileOutputData;

import java.util.ArrayList;
import java.util.List;

/** Formats the use case result into text for the view model. */
public final class ManageProfilePresenter implements ManageProfileOutputBoundary {
    private final ManageProfileViewModel viewModel;

    public ManageProfilePresenter(ManageProfileViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareLoadedView(ManageProfileOutputData data) {
        show(data, "Loaded your saved profile.", false);
    }

    @Override
    public void prepareSavedView(ManageProfileOutputData data) {
        show(data, "Profile saved. New searches use it.", false);
    }

    @Override
    public void prepareConfirmView(ManageProfileOutputData data, String reason) {
        show(data, reason + " Until you save one, searches use the settings below. "
                + "Check them and select Save profile.", true);
    }

    /** Keeps the settings and messages already shown, so the passenger can correct their choice. */
    @Override
    public void prepareFailView(String errorMessage) {
        viewModel.setState(viewModel.getState().withError(errorMessage));
        viewModel.firePropertyChanged();
    }

    private void show(ManageProfileOutputData data, String status, boolean needsConfirmation) {
        viewModel.setState(new ManageProfileState(data.isWheelchairRequired(), data.isStairsAllowed(),
                data.isEscalatorsAllowed(), data.getMaxTransfers(), status, details(data), "", needsConfirmation));
        viewModel.firePropertyChanged();
    }

    private List<String> details(ManageProfileOutputData data) {
        List<String> details = new ArrayList<>();
        if (data.isStepFreeRouting()) {
            details.add("You board, change lines and exit only at stations the TTC confirms as accessible.");
        } else {
            details.add("Any station can be used, including stations the TTC does not confirm as step-free.");
        }
        details.add("Routes have at most " + data.getMaxTransfers()
                + (data.getMaxTransfers() == 1 ? " transfer." : " transfers."));
        if (data.isEscalatorsAllowed() && !data.isStairsAllowed()) {
            details.add("The station data does not include escalators yet, so allowing escalators "
                    + "does not change your routes.");
        }
        return details;
    }
}
