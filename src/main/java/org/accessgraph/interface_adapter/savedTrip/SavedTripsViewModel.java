package org.accessgraph.interface_adapter.savedTrip;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/** Observable view model, matching the existing plan-route and reroute adapters. */
public final class SavedTripsViewModel {
    public static final String STATE_PROPERTY = "state";
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private SavedTripsState state = SavedTripsState.empty();

    public SavedTripsState getState() { return state; }
    public void setState(SavedTripsState state) { this.state = state; }
    public void firePropertyChanged() { changes.firePropertyChange(STATE_PROPERTY, null, state); }
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }
}
