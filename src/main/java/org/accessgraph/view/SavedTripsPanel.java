package org.accessgraph.view;

import org.accessgraph.interface_adapter.savedTrip.SavedTripsController;
import org.accessgraph.interface_adapter.savedTrip.SavedTripsState;
import org.accessgraph.interface_adapter.savedTrip.SavedTripsViewModel;
import org.accessgraph.interface_adapter.plan_route.PlanRouteController;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

/** Third journey tab: persists a named trip and shows side-by-side-in-time route comparisons. */
public final class SavedTripsPanel extends JPanel {
    private final SavedTripsController controller;
    private final SavedTripsViewModel viewModel;
    private final PlanRoutePanel planner;
    private final PlanRouteController routeController;
    private final JTextField tripName = new JTextField(18);
    private final DefaultListModel<SavedTripsState.TripChoice> choices = new DefaultListModel<>();
    private final JList<SavedTripsState.TripChoice> trips = new JList<>(choices);
    private final JLabel notice = Ui.label(" ", 12, false);
    private final JLabel error = Ui.label(" ", 12, false);
    private final JTextArea comparison = new JTextArea();

    public SavedTripsPanel(SavedTripsController controller, SavedTripsViewModel viewModel,
                           PlanRoutePanel planner, PlanRouteController routeController) {
        super(new BorderLayout(0, 8));
        this.controller = controller;
        this.viewModel = viewModel;
        this.planner = planner;
        this.routeController = routeController;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.PAPER);
        setPreferredSize(new Dimension(320, 0));

        JButton save = Ui.button("Save current trip", true);
        JButton load = Ui.button("Load / compare", false);
        JButton delete = Ui.button("Delete selected", false);
        save.addActionListener(event -> controller.save(tripName.getText(),
                planner.getSelectedOriginId(), planner.getSelectedDestinationId()));
        load.addActionListener(event -> {
            SavedTripsState.TripChoice chosen = trips.getSelectedValue();
            if (chosen == null) {
                error.setText("Choose a saved trip first.");
                return;
            }
            controller.load(chosen.getId());
            SavedTripsState state = viewModel.getState();
            if (state.getError().isEmpty() && state.getSelectedId() != null
                    && planner.selectStations(state.getOriginId(), state.getDestinationId())) {
                routeController.execute(state.getOriginId(), state.getDestinationId());
            }
        });
        delete.addActionListener(event -> {
            SavedTripsState.TripChoice chosen = trips.getSelectedValue();
            if (chosen == null) {
                error.setText("Choose a saved trip first.");
                return;
            }
            controller.delete(chosen.getId());
        });
        tripName.getAccessibleContext().setAccessibleName("Saved trip name");
        trips.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        trips.setFont(Ui.font(13, false));
        trips.getAccessibleContext().setAccessibleName("Saved journeys");
        comparison.setEditable(false);
        comparison.setLineWrap(true);
        comparison.setWrapStyleWord(true);
        comparison.setFont(Ui.font(13, false));
        comparison.getAccessibleContext().setAccessibleName("Saved versus current route comparison");
        notice.setForeground(Ui.MUTED);
        error.setForeground(Ui.ERROR);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(left(Ui.label("Saved trips", 18, true)));
        form.add(left(Ui.label("Uses the stations selected in Plan route", 12, false)));
        form.add(left(Ui.label("Name", 13, false)));
        form.add(left(tripName));
        form.add(left(save));
        form.add(left(Ui.label("Saved journeys", 13, false)));
        JScrollPane listScroll = new JScrollPane(trips);
        listScroll.setPreferredSize(new Dimension(280, 120));
        form.add(left(listScroll));
        form.add(left(load));
        form.add(left(delete));
        form.add(left(notice));
        form.add(left(error));
        add(form, BorderLayout.NORTH);
        add(new JScrollPane(comparison), BorderLayout.CENTER);
        viewModel.addPropertyChangeListener(event -> show(viewModel.getState()));
        controller.list();
    }

    private void show(SavedTripsState state) {
        String originallySelected = trips.getSelectedValue() == null ? null : trips.getSelectedValue().getId();
        choices.clear();
        for (SavedTripsState.TripChoice choice : state.getTrips()) {
            choices.addElement(choice);
        }
        String preferredId = state.getSelectedId() != null ? state.getSelectedId() : originallySelected;
        if (preferredId != null) {
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).getId().equals(preferredId)) {
                    trips.setSelectedIndex(i);
                    break;
                }
            }
        }
        notice.setText(state.getNotice().isEmpty() ? " " : state.getNotice());
        error.setText(state.getError().isEmpty() ? " " : state.getError());
        comparison.setText(state.getComparison());
        comparison.setCaretPosition(0);
    }

    private static Component left(javax.swing.JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }
}
