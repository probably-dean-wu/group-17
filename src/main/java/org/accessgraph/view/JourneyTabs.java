package org.accessgraph.view;

import org.accessgraph.interface_adapter.reroute.RerouteViewModel;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;

/**
 * The column beside the map: one tab plans a route and the other simulates disruptions. The second tab's
 * title counts the active disruptions, so the passenger can tell while planning that simulated conditions
 * are in effect.
 */
public final class JourneyTabs extends JTabbedPane {
    private static final String DISRUPTIONS = "Disruptions";


    public JourneyTabs(
            JComponent routePlanner,
            JComponent disruptions,
            RerouteViewModel viewModel) {

        this(routePlanner, disruptions, null, viewModel);
    }

    /** Adds Saved trips without breaking existing callers. */
    public JourneyTabs(
            JComponent routePlanner,
            JComponent disruptions,
            JComponent savedTrips,
            RerouteViewModel viewModel) {

        setFont(Ui.font(13, true));
        setBackground(Ui.PAPER);
        setOpaque(true);

        addTab("Plan route", routePlanner);
        addTab(DISRUPTIONS, disruptions);

        if (savedTrips != null) {
            addTab("Saved trips", savedTrips);
        }

        viewModel.addPropertyChangeListener(event -> {
            int active =
                    viewModel.getState().getActiveDisruptions().size();

            setTitleAt(
                    1,
                    active == 0
                            ? DISRUPTIONS
                            : DISRUPTIONS + " (" + active + ")"
            );
        });
    }

}
