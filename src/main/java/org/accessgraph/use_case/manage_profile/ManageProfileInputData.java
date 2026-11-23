package org.accessgraph.use_case.manage_profile;

/** The accessibility needs the passenger chose. */
public final class ManageProfileInputData {
    private final boolean wheelchairRequired;
    private final boolean stairsAllowed;
    private final boolean escalatorsAllowed;
    private final int maxTransfers;

    public ManageProfileInputData(boolean wheelchairRequired, boolean stairsAllowed, boolean escalatorsAllowed,
                                  int maxTransfers) {
        this.wheelchairRequired = wheelchairRequired;
        this.stairsAllowed = stairsAllowed;
        this.escalatorsAllowed = escalatorsAllowed;
        this.maxTransfers = maxTransfers;
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
}
