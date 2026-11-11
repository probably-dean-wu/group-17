package org.accessgraph.view;

import org.accessgraph.interface_adapter.PrototypeController;
import org.accessgraph.interface_adapter.PrototypeState;
import org.accessgraph.interface_adapter.PrototypeState.SavedTrip;
import org.accessgraph.interface_adapter.PrototypeState.Screen;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Dimension;

final class SavedTripsPanel extends JPanel {
    private final DefaultListModel<SavedTrip> listModel = new DefaultListModel<>();
    private final JList<SavedTrip> trips = new JList<>(listModel);
    private final JButton load = Ui.button("Load selected trip", true);
    private final JButton delete = Ui.button("Delete selected trip", false);
    private final JTextArea empty = Ui.text("No trips saved yet. Choose two stations and give the pair a name in the route planner.");
    private final JTextArea notice = Ui.text("");

    SavedTripsPanel(PrototypeState state, PrototypeController controller) {
        super(new BorderLayout());
        JPanel card = Ui.card();
        Ui.add(card, Ui.label("Your station pairs", 20, true), 12);
        Ui.add(card, Ui.text("Save shortcuts for journeys you use often. Only station pairs are saved; "
                + "route results and accessibility assessments are not stored."), 18);
        Ui.add(card, empty, 14);
        trips.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        trips.setVisibleRowCount(5);
        trips.setFixedCellHeight(78);
        trips.getAccessibleContext().setAccessibleName("Saved station pairs");
        trips.setCellRenderer((list, trip, index, selected, focus) -> {
            JPanel row = new JPanel(new BorderLayout(0, 5));
            row.setOpaque(true);
            row.setBackground(selected ? new Color(0xD7ECEA) : Color.WHITE);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(focus ? Ui.TEAL : Ui.LINE, focus ? 2 : 1),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)));
            row.add(Ui.label(trip.getName(), 16, true), BorderLayout.NORTH);
            row.add(Ui.label(trip.getOrigin() + " → " + trip.getDestination(), 15, false), BorderLayout.CENTER);
            row.getAccessibleContext().setAccessibleName(trip.getName() + ", "
                    + trip.getOrigin() + " to " + trip.getDestination());
            row.setSize(Math.max(1, list.getWidth()), 78);
            row.doLayout();
            return row;
        });
        javax.swing.JScrollPane listScroll = Ui.scroll(trips);
        listScroll.setPreferredSize(new Dimension(400, 240));
        listScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        Ui.add(card, listScroll, 18);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(load);
        actions.add(javax.swing.Box.createHorizontalStrut(12));
        actions.add(delete);
        Ui.add(card, actions, 18);
        Ui.add(card, notice, 16);
        JButton planner = Ui.button("Go to route planner", false);
        planner.addActionListener(event -> controller.navigate(Screen.PLANNER));
        Ui.add(card, planner, 0);
        load.addActionListener(event -> controller.loadTrip(trips.getSelectedValue()));
        delete.addActionListener(event -> controller.deleteTrip(trips.getSelectedValue()));
        trips.addListSelectionListener(event -> updateActions());
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(card, BorderLayout.NORTH);
        add(Ui.scroll(Ui.page("Saved trips", "Keep frequent station pairs handy during this session. "
                + "Closing AccessGraph clears this list.", body)));
        state.addChangeListener(event -> refresh(state));
        refresh(state);
    }

    private void refresh(PrototypeState state) {
        SavedTrip selected = trips.getSelectedValue();
        listModel.clear();
        state.getSavedTrips().forEach(listModel::addElement);
        if (selected != null && state.getSavedTrips().contains(selected)) {
            trips.setSelectedValue(selected, true);
        }
        empty.setVisible(listModel.isEmpty());
        Ui.notice(notice, state.getNotice(Screen.SAVED_TRIPS), state.isError(Screen.SAVED_TRIPS));
        updateActions();
    }

    private void updateActions() {
        load.setEnabled(trips.getSelectedValue() != null);
        delete.setEnabled(trips.getSelectedValue() != null);
    }
}
