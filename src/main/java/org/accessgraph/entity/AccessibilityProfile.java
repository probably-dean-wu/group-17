package org.accessgraph.entity;

/** Immutable preferences; this value object does not evaluate route accessibility. */
public final class AccessibilityProfile {
    private final boolean wheelchairRequired;
    private final boolean stairsAllowed;
    private final boolean escalatorsAllowed;
    private final int maxTransfers;

    public AccessibilityProfile(boolean wheelchairRequired, boolean stairsAllowed,
                                boolean escalatorsAllowed, int maxTransfers) {
        if (maxTransfers < 0 || maxTransfers > 5) {
            throw new IllegalArgumentException("Choose between 0 and 5 transfers.");
        }
        if (wheelchairRequired && (stairsAllowed || escalatorsAllowed)) {
            throw new IllegalArgumentException("Wheelchair journeys cannot require stairs or escalators.");
        }
        this.wheelchairRequired = wheelchairRequired;
        this.stairsAllowed = stairsAllowed;
        this.escalatorsAllowed = escalatorsAllowed;
        this.maxTransfers = maxTransfers;
    }

    public static AccessibilityProfile defaults() {
        return new AccessibilityProfile(true, false, false, 2);
    }

    public boolean isWheelchairRequired() { return wheelchairRequired; }
    public boolean isStairsAllowed() { return stairsAllowed; }
    public boolean isEscalatorsAllowed() { return escalatorsAllowed; }
    public int getMaxTransfers() { return maxTransfers; }
}
