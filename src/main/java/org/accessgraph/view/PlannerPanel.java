package org.accessgraph.view;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.interface_adapter.PrototypeController;
import org.accessgraph.interface_adapter.PrototypeState;
import org.accessgraph.interface_adapter.PrototypeState.Screen;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.FlowLayout;

final class PlannerPanel extends JPanel {
    private static final String SELECT_STATION = "Select a station…";
    private final PrototypeState state;
    private final PrototypeController controller;
    private final JComboBox<String> origin = stationPicker();
    private final JComboBox<String> destination = stationPicker();
    private final JTextField tripName = new JTextField();
    private final JTextArea profileSummary = Ui.text("");
    private final JTextArea notice = Ui.text("");
    private final JTextArea endpoints = Ui.text("");
    private final JTextArea previewStatus = Ui.text("");
    private final JTextArea simulationStatus = Ui.text("");
    private boolean refreshing;

    PlannerPanel(PrototypeState state, PrototypeController controller) {
        super(new BorderLayout());
        this.state = state;
        this.controller = controller;
        JPanel columns = new JPanel(new GridBagLayout());
        columns.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0.48;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 20);
        columns.add(buildForm(), constraints);
        constraints.gridx = 1;
        constraints.weightx = 0.52;
        constraints.insets = new Insets(0, 0, 0, 0);
        columns.add(buildPreview(), constraints);
        add(Ui.scroll(Ui.page("Plan your journey",
                "Choose a station pair and your travel preferences. Station names are sample inputs only.",
                columns)), BorderLayout.CENTER);
        origin.addActionListener(event -> selectionChanged());
        destination.addActionListener(event -> selectionChanged());
        state.addChangeListener(event -> refresh());
        refresh();
    }

    private JPanel buildForm() {
        JPanel form = Ui.card();
        Ui.add(form, Ui.label("Where are you going?", 20, true), 18);
        Ui.field(form, "Origin", origin);
        Ui.field(form, "Destination", destination);
        Ui.add(form, Ui.label("Your accessibility preferences", 16, true), 8);
        JButton edit = Ui.button("Edit preferences", false);
        edit.addActionListener(event -> controller.navigate(Screen.PROFILE));
        Ui.add(form, profileSummary, 8);
        Ui.add(form, edit, 14);
        JButton search = Ui.button("Find route", true);
        search.setMnemonic('F');
        search.addActionListener(event -> controller.requestRoute());
        JButton swap = Ui.button("Swap stations", false);
        swap.setMnemonic('W');
        swap.addActionListener(event -> controller.swapStations());
        JPanel journeyActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        journeyActions.setOpaque(false);
        journeyActions.add(search);
        journeyActions.add(javax.swing.Box.createHorizontalStrut(10));
        journeyActions.add(swap);
        journeyActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, search.getPreferredSize().height));
        Ui.add(form, journeyActions, 12);
        Ui.add(form, notice, 16);
        javax.swing.JLabel nameLabel = Ui.label("Trip name (session only)", 15, true);
        nameLabel.setLabelFor(tripName);
        tripName.getAccessibleContext().setAccessibleName("Trip name (session only)");
        Ui.add(form, nameLabel, 7);
        tripName.setToolTipText("Name this station pair using 1–48 characters.");
        JButton save = Ui.button("Save station pair", false);
        save.addActionListener(event -> {
            if (controller.saveTrip(tripName.getText())) { tripName.setText(""); }
        });
        tripName.addActionListener(event -> save.doClick());
        JPanel saveRow = new JPanel(new BorderLayout(10, 0));
        saveRow.setOpaque(false);
        saveRow.add(tripName, BorderLayout.CENTER);
        saveRow.add(save, BorderLayout.EAST);
        saveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, save.getPreferredSize().height));
        Ui.add(form, saveRow, 0);
        return form;
    }

    private JPanel buildPreview() {
        JPanel preview = Ui.card();
        Ui.add(preview, Ui.label("Journey preview", 20, true), 8);
        Ui.add(preview, previewStatus, 22);
        JPanel pair = new JPanel(new BorderLayout());
        pair.setBackground(Ui.BACKGROUND);
        pair.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        pair.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        endpoints.setFont(Ui.BODY.deriveFont(java.awt.Font.BOLD, 18));
        endpoints.setForeground(Ui.INK);
        pair.add(endpoints, BorderLayout.CENTER);
        Ui.add(preview, pair, 10);
        Ui.add(preview, Ui.text("Selected endpoints only. No path has been calculated."), 24);
        Ui.add(preview, Ui.label("Estimated duration", 15, true), 4);
        Ui.add(preview, Ui.text("Not calculated"), 16);
        Ui.add(preview, Ui.label("Transfers", 15, true), 4);
        Ui.add(preview, Ui.text("Not calculated"), 16);
        Ui.add(preview, Ui.label("Accessibility", 15, true), 4);
        JTextArea unknown = Ui.text("Unknown — station and connection accessibility have not been verified.");
        unknown.setForeground(Ui.AMBER);
        Ui.add(preview, unknown, 20);
        Ui.add(preview, Ui.label("Network conditions", 15, true), 4);
        Ui.add(preview, simulationStatus, 18);
        JButton compare = Ui.button("Compare routes (coming later)", false);
        compare.setEnabled(false);
        compare.setToolTipText("Requires routing and before/after route results.");
        Ui.add(preview, compare, 12);
        Ui.add(preview, Ui.text("Data source: bundled sample names and fictional disruption controls. "
                + "No live or cached TTC data is connected."), 0);
        return preview;
    }

    private static JComboBox<String> stationPicker() {
        JComboBox<String> picker = new JComboBox<>();
        picker.addItem(SELECT_STATION);
        PrototypeState.STATIONS.forEach(picker::addItem);
        picker.setBackground(Color.WHITE);
        return picker;
    }

    private void selectionChanged() {
        if (!refreshing) {
            controller.selectJourney(selected(origin), selected(destination));
        }
    }

    private String selected(JComboBox<String> picker) {
        return picker.getSelectedIndex() <= 0 ? "" : (String) picker.getSelectedItem();
    }

    private void refresh() {
        refreshing = true;
        try {
            origin.setSelectedItem(state.getOrigin().isEmpty() ? SELECT_STATION : state.getOrigin());
            destination.setSelectedItem(state.getDestination().isEmpty() ? SELECT_STATION : state.getDestination());
        } finally {
            refreshing = false;
        }
        AccessibilityProfile profile = state.getProfile();
        profileSummary.setText((profile.isWheelchairRequired() ? "Wheelchair access required" : "Wheelchair access optional")
                + "\nStairs " + (profile.isStairsAllowed() ? "permitted" : "excluded")
                + "; escalators " + (profile.isEscalatorsAllowed() ? "permitted" : "excluded")
                + "\nMaximum transfers: " + profile.getMaxTransfers());
        Ui.notice(notice, state.getNotice(Screen.PLANNER), state.isError(Screen.PLANNER));
        endpoints.setText("From   " + (state.getOrigin().isEmpty() ? "Choose origin" : state.getOrigin())
                + "\n\nTo        " + (state.getDestination().isEmpty() ? "Choose destination" : state.getDestination()));
        previewStatus.setText(state.isRequestReviewed()
                ? "Request reviewed · routing not connected" : "Waiting for a journey request");
        simulationStatus.setText(state.getActiveDisruptionCount() + " sample connection(s) marked unavailable.\n"
                + "Simulation only; real service conditions are unknown.");
    }
}
