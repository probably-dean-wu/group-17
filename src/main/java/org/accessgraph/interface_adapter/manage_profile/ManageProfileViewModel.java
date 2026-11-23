package org.accessgraph.interface_adapter.manage_profile;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/** Holds the state for the profile view and notifies it of changes (Observer pattern). */
public final class ManageProfileViewModel {
    public static final String STATE_PROPERTY = "state";
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private ManageProfileState state = ManageProfileState.empty();

    public ManageProfileState getState() {
        return state;
    }

    public void setState(ManageProfileState state) {
        this.state = state;
    }

    public void firePropertyChanged() {
        support.firePropertyChange(STATE_PROPERTY, null, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }
}
