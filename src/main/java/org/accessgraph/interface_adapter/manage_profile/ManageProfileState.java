package org.accessgraph.interface_adapter.manage_profile;

import java.util.List;

/**
 * What the profile view shows: the settings in the form, a status line, what the profile means for routes,
 * and an error. {@code needsConfirmation} is true when no usable profile was saved and the passenger should
 * confirm one.
 */
public final class ManageProfileState {
    private final boolean wheelchairRequired;
    private final boolean stairsAllowed;
    private final boolean escalatorsAllowed;
    private final int maxTransfers;
    private final String status;
    private final List<String> details;
    private final String error;
    private final boolean needsConfirmation;

    public ManageProfileState(boolean wheelchairRequired, boolean stairsAllowed, boolean escalatorsAllowed,
                              int maxTransfers, String status, List<String> details, String error,
                              boolean needsConfirmation) {
        this.wheelchairRequired = wheelchairRequired;
        this.stairsAllowed = stairsAllowed;
        this.escalatorsAllowed = escalatorsAllowed;
        this.maxTransfers = maxTransfers;
        this.status = status;
        this.details = List.copyOf(details);
        this.error = error;
        this.needsConfirmation = needsConfirmation;
    }

    /** The form before anything has been loaded: the strictest settings and no messages. */
    public static ManageProfileState empty() {
        return new ManageProfileState(true, false, false, 2, "", List.of(), "", false);
    }

    /** The same settings and messages with a different error. */
    public ManageProfileState withError(String newError) {
        return new ManageProfileState(wheelchairRequired, stairsAllowed, escalatorsAllowed, maxTransfers, status,
                details, newError, needsConfirmation);
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

    public String getStatus() {
        return status;
    }

    /** What the profile means for the routes the passenger is offered, one line of text per entry. */
    public List<String> getDetails() {
        return details;
    }

    public String getError() {
        return error;
    }

    public boolean isNeedsConfirmation() {
        return needsConfirmation;
    }
}
