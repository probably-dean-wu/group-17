package org.accessgraph.entity;

/**
 * A passenger's routing requirements: whether they use a wheelchair, whether stairs and escalators are
 * acceptable, and how many times they are willing to change lines.
 *
 * <p>The station data only says whether the TTC confirms a station as accessible. It has no escalator
 * information, so a passenger who cannot use stairs is limited to confirmed stations even when escalators
 * are acceptable. Missing information is never treated as accessible.
 */
public final class AccessibilityProfile {
    /** The largest transfer limit a passenger can choose. */
    public static final int MAX_TRANSFERS = 3;

    private final boolean wheelchairRequired;
    private final boolean stairsAllowed;
    private final boolean escalatorsAllowed;
    private final int maxTransfers;

    /**
     * Creates a profile from the passenger's choices.
     *
     * @throws IllegalArgumentException when a wheelchair profile allows stairs or escalators, or the
     *                                  transfer limit is outside 0 to {@link #MAX_TRANSFERS}
     */
    public AccessibilityProfile(boolean wheelchairRequired, boolean stairsAllowed, boolean escalatorsAllowed,
                                int maxTransfers) {
        if (wheelchairRequired && (stairsAllowed || escalatorsAllowed)) {
            throw new IllegalArgumentException("A wheelchair profile cannot allow stairs or escalators.");
        }
        if (maxTransfers < 0) {
            throw new IllegalArgumentException("Maximum transfers cannot be negative.");
        }
        if (maxTransfers > MAX_TRANSFERS) {
            throw new IllegalArgumentException("Maximum transfers cannot be more than " + MAX_TRANSFERS + ".");
        }
        this.wheelchairRequired = wheelchairRequired;
        this.stairsAllowed = stairsAllowed;
        this.escalatorsAllowed = escalatorsAllowed;
        this.maxTransfers = maxTransfers;
    }

    /**
     * Shorthand for the two kinds of profile the route planner started with: a step-free profile is a
     * wheelchair profile, and any other profile allows stairs and escalators.
     */
    public AccessibilityProfile(boolean stepFreeRequired, int maxTransfers) {
        this(stepFreeRequired, !stepFreeRequired, !stepFreeRequired, maxTransfers);
    }

    /** The safest profile, used until the passenger saves their own: wheelchair access and two transfers. */
    public static AccessibilityProfile strictest() {
        return new AccessibilityProfile(true, false, false, 2);
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

    /**
     * True when the passenger can only board, transfer and exit at confirmed accessible stations. That is
     * the case for wheelchair users and for anyone who cannot use stairs, because the station data cannot
     * show that a station is usable by escalator alone.
     */
    public boolean isStepFreeRequired() {
        return wheelchairRequired || !stairsAllowed;
    }

    public int getMaxTransfers() {
        return maxTransfers;
    }

    /** Whether the passenger may board, exit or change lines at this station. */
    public boolean canUse(Station station) {
        return !isStepFreeRequired() || station.getAccessibility() == Station.Accessibility.CONFIRMED;
    }
}
