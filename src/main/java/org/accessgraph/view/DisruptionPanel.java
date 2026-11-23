package org.accessgraph.view;

import org.accessgraph.interface_adapter.reroute.LiveAlertsController;
import org.accessgraph.interface_adapter.reroute.RerouteController;
import org.accessgraph.interface_adapter.reroute.RerouteState;
import org.accessgraph.interface_adapter.reroute.RerouteViewModel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets the passenger simulate a disruption on one connection and restore it afterwards, load or clear the
 * TTC's live alerts, and read what each change means for the journey they last searched for.
 */
public final class DisruptionPanel extends JPanel {
    private static final String SIMULATED_ONLY = "Simulated conditions. Not live TTC data.";
    private final List<ConnectionChoice> connections;
    private final JComboBox<String> line = new JComboBox<>();
    private final JComboBox<ConnectionChoice> connection = new JComboBox<>();
    private final JComboBox<TypeChoice> type;
    private final DefaultListModel<RerouteState.ActiveDisruption> active = new DefaultListModel<>();
    private final JList<RerouteState.ActiveDisruption> activeList = new JList<>(active);
    private final JButton restore = Ui.button("Restore selected", false);
    private final JButton clearLive = Ui.button("Clear", false);
    private final JLabel conditions = Ui.label(SIMULATED_ONLY, 12, false);
    private final JTextArea result = new JTextArea();

    /** A connection shown in the drop-downs: the ids are sent to the controller, the label is displayed. */
    public static final class ConnectionChoice {
        private final String line;
        private final String stationId;
        private final String otherStationId;
        private final String label;

        public ConnectionChoice(String line, String stationId, String otherStationId, String label) {
            this.line = line;
            this.stationId = stationId;
            this.otherStationId = otherStationId;
            this.label = label;
        }

        public String getLine() {
            return line;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** A disruption type shown in the drop-down: the id is sent to the controller, the label is displayed. */
    public static final class TypeChoice {
        private final String id;
        private final String label;

        public TypeChoice(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public DisruptionPanel(List<ConnectionChoice> connections, List<TypeChoice> types,
                           RerouteController controller, LiveAlertsController liveAlerts,
                           RerouteViewModel viewModel) {
        super(new BorderLayout(0, 8));
        this.connections = List.copyOf(connections);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.PAPER);
        setPreferredSize(new Dimension(320, 0));

        Set<String> lines = new LinkedHashSet<>();
        for (ConnectionChoice choice : this.connections) {
            lines.add(choice.line);
        }
        lines.forEach(line::addItem);
        line.addActionListener(event -> showConnectionsOnSelectedLine());
        showConnectionsOnSelectedLine();
        type = new JComboBox<>(types.toArray(new TypeChoice[0]));

        JButton simulate = Ui.button("Simulate disruption", true);
        simulate.addActionListener(event -> {
            ConnectionChoice chosen = (ConnectionChoice) connection.getSelectedItem();
            TypeChoice chosenType = (TypeChoice) type.getSelectedItem();
            controller.simulate(chosen == null ? null : chosen.line, chosen == null ? null : chosen.stationId,
                    chosen == null ? null : chosen.otherStationId, chosenType == null ? null : chosenType.id);
        });
        restore.setEnabled(false);
        restore.addActionListener(event -> {
            RerouteState.ActiveDisruption chosen = activeList.getSelectedValue();
            if (chosen != null) {
                controller.restore(chosen.getLine(), chosen.getStationId(), chosen.getOtherStationId());
            }
        });

        activeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        activeList.setFont(Ui.font(13, false));
        activeList.setFixedCellHeight(22);
        activeList.setVisibleRowCount(3);
        activeList.getAccessibleContext().setAccessibleName("Active disruptions");
        activeList.addListSelectionListener(event -> restore.setEnabled(activeList.getSelectedValue() != null));

        JButton loadLive = Ui.button("Load live TTC alerts", false);
        loadLive.addActionListener(event -> {
            // The request can take a few seconds, so say what is happening before it starts.
            result.setForeground(Ui.INK);
            result.setText("Loading live TTC alerts…");
            SwingUtilities.invokeLater(liveAlerts::load);
        });
        clearLive.setEnabled(false);
        clearLive.setToolTipText("Stop applying live TTC alerts");
        clearLive.getAccessibleContext().setAccessibleName("Clear live TTC alerts");
        clearLive.addActionListener(event -> liveAlerts.clear());
        JPanel live = new JPanel(new BorderLayout(8, 0));
        live.setOpaque(false);
        live.add(loadLive, BorderLayout.CENTER);
        live.add(clearLive, BorderLayout.EAST);
        conditions.setForeground(Ui.MUTED);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(left(Ui.label("Disruptions", 18, true)));
        form.add(left(conditions));
        form.add(Box.createVerticalStrut(8));
        form.add(left(live));
        addField(form, "Line", line);
        addField(form, "Between", connection);
        addField(form, "Type", type);
        form.add(Box.createVerticalStrut(8));
        form.add(left(simulate));
        addField(form, "Active disruptions", new JScrollPane(activeList));
        form.add(Box.createVerticalStrut(8));
        form.add(left(restore));

        result.setEditable(false);
        result.setLineWrap(true);
        result.setWrapStyleWord(true);
        result.setFont(Ui.font(13, false));
        result.getAccessibleContext().setAccessibleName("Effect on your journey");
        result.setText("Simulate a disruption or load the live TTC alerts to see how they change your journey.");
        add(form, BorderLayout.NORTH);
        add(new JScrollPane(result), BorderLayout.CENTER);

        viewModel.addPropertyChangeListener(event -> show(viewModel.getState()));
    }

    /** Fills the connection drop-down with the connections on the line that is selected. */
    private void showConnectionsOnSelectedLine() {
        connection.removeAllItems();
        for (ConnectionChoice choice : connections) {
            if (choice.line.equals(line.getSelectedItem())) {
                connection.addItem(choice);
            }
        }
    }

    private void show(RerouteState state) {
        active.clear();
        for (RerouteState.ActiveDisruption disruption : state.getActiveDisruptions()) {
            active.addElement(disruption);
        }
        if (!active.isEmpty()) {
            activeList.setSelectedIndex(0);
        }
        restore.setEnabled(!active.isEmpty());
        conditions.setText(state.getLiveStatus().isEmpty() ? SIMULATED_ONLY : state.getLiveStatus());
        clearLive.setEnabled(!state.getLiveStatus().isEmpty());

        boolean failed = !state.getError().isEmpty();
        List<String> lines = new ArrayList<>();
        if (failed) {
            lines.add(state.getError());
        } else {
            lines.add(state.getHeadline());
            if (!state.getDetails().isEmpty()) {
                lines.add("");
                lines.addAll(state.getDetails());
            }
        }
        result.setForeground(failed ? Ui.ERROR : Ui.INK);
        result.setText(String.join("\n", lines));
        result.setCaretPosition(0);
    }

    private static void addField(JPanel form, String name, JComponent field) {
        JLabel label = Ui.label(name, 13, false);
        label.setLabelFor(field instanceof JScrollPane ? ((JScrollPane) field).getViewport().getView() : field);
        form.add(Box.createVerticalStrut(8));
        form.add(left(label));
        form.add(left(field));
    }

    private static Component left(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }
}
