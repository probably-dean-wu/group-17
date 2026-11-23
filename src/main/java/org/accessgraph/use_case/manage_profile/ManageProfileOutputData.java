package org.accessgraph.use_case.manage_profile;

import org.accessgraph.entity.AccessibilityProfile;

/** The profile that searches now use, as plain data the presenter can format. */
public final class ManageProfileOutputData {
    private final boolean wheelchairRequired;
    private final boolean stairsAllowed;
    private final boolean escalatorsAllowed;
    private final int maxTransfers;
    private final boolean stepFreeRouting;

    public ManageProfileOutputData(boolean wheelchairRequired, boolean stairsAllowed, boolean escalatorsAllowed,
                                   int maxTransfers, boolean stepFreeRouting) {
        this.wheelchairRequired = wheelchairRequired;
        this.stairsAllowed = stairsAllowed;
        this.escalatorsAllowed = escalatorsAllowed;
        this.maxTransfers = maxTransfers;
        this.stepFreeRouting = stepFreeRouting;
    }

    static ManageProfileOutputData of(AccessibilityProfile profile) {
        return new ManageProfileOutputData(profile.isWheelchairRequired(), profile.isStairsAllowed(),
                profile.isEscalatorsAllowed(), profile.getMaxTransfers(), profile.isStepFreeRequired());
    }

    public boolean isWheelchairRequired() {
        return wheelchairRequired;
    }

    public boolean isStairsAllowed() {
        return stairsAllowed;
    }

    public boolean isEscalatorsAllowed() {
        return escalatorsAllowed;
    }

    public int getMaxTransfers() {
        return maxTransfers;
    }

    /** True when searches only board, change lines and exit at stations the TTC confirms as accessible. */
    public boolean isStepFreeRouting() {
        return stepFreeRouting;
    }
}
