package org.accessgraph.use_case.manage_profile;

/** The saved profile could not be read or written. The message says why in words a passenger understands. */
public final class ProfileDataException extends Exception {
    public ProfileDataException(String message) {
        super(message);
    }

    public ProfileDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
