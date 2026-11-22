package org.accessgraph.view;

import org.accessgraph.interface_adapter.plan_route.PlanRouteController;
import org.accessgraph.interface_adapter.plan_route.PlanRouteState;
import org.accessgraph.interface_adapter.plan_route.PlanRouteViewModel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;

/** Lets the passenger pick two stations and shows the planned route. */
public final class PlanRoutePanel extends JPanel {
    private final JComboBox<StationChoice> origin;
    private final JComboBox<StationChoice> destination;
    private final JLabel summary = Ui.label(" ", 14, true);
    private final JLabel error = Ui.label(" ", 13, false);
    private final JTextArea steps = new JTextArea();

    /** A station shown in the drop-downs: the id is sent to the controller, the name is displayed. */
    public static final class StationChoice {
        private final String id;
        private final String name;

        public StationChoice(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public PlanRoutePanel(List<StationChoice> stations, PlanRouteController controller,
                          PlanRouteViewModel viewModel) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.PAPER);
        setPreferredSize(new Dimension(320, 0));
        origin = new JComboBox<>(stations.toArray(new StationChoice[0]));
        destination = new JComboBox<>(stations.toArray(new StationChoice[0]));
        JButton search = Ui.button("Find route", true);
        search.addActionListener(event -> controller.execute(selectedId(origin), selectedId(destination)));
        error.setForeground(Ui.ERROR);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(left(Ui.label("Plan an accessible route", 18, true)));
        form.add(left(Ui.label("From", 13, false)));
        form.add(left(origin));
        form.add(left(Ui.label("To", 13, false)));
        form.add(left(destination));
        form.add(left(search));
        form.add(left(summary));
        form.add(left(error));

        steps.setEditable(false);
        steps.setLineWrap(true);
        steps.setWrapStyleWord(true);
        steps.setFont(Ui.font(13, false));
        add(form, BorderLayout.NORTH);
        add(new JScrollPane(steps), BorderLayout.CENTER);

        viewModel.addPropertyChangeListener(event -> show(viewModel.getState()));
    }

    private void show(PlanRouteState state) {
        summary.setText(state.getSummary().isEmpty() ? " " : state.getSummary());
        error.setText(state.getError().isEmpty() ? " " : "<html>" + state.getError() + "</html>");
        steps.setText(String.join("\n", state.getSteps()));
        steps.setCaretPosition(0);
    }


    /** Returns the currently selected origin station ID. */
    public String getSelectedOriginId() {
        return selectedId(origin);
    }

    /** Returns the currently selected destination station ID. */
    public String getSelectedDestinationId() {
        return selectedId(destination);
    }

    /** Updates the existing selectors to display a saved trip. */
    public boolean selectStations(String originId, String destinationId) {
        StationChoice from = findChoice(origin, originId);
        StationChoice to = findChoice(destination, destinationId);

        if (from == null || to == null) {
            return false;
        }

        origin.setSelectedItem(from);
        destination.setSelectedItem(to);
        return true;
    }

    /** Finds the station choice with the matching station ID. */
    private static StationChoice findChoice(
            JComboBox<StationChoice> box, String id) {

        for (int i = 0; i < box.getItemCount(); i++) {
            StationChoice choice = box.getItemAt(i);

            if (choice.id.equals(id)) {
                return choice;
            }
        }

        return null;
    }


    private static String selectedId(JComboBox<StationChoice> box) {
        StationChoice choice = (StationChoice) box.getSelectedItem();
        return choice == null ? null : choice.id;
    }

    private static Component left(javax.swing.JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }
}
