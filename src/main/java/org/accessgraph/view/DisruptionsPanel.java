package org.accessgraph.view;

import org.accessgraph.interface_adapter.PrototypeController;
import org.accessgraph.interface_adapter.PrototypeState;
import org.accessgraph.interface_adapter.PrototypeState.Screen;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;

final class DisruptionsPanel extends JPanel {
    private final Map<String, JCheckBox> controls = new LinkedHashMap<>();
    private final JTextArea notice = Ui.text("");

    DisruptionsPanel(PrototypeState state, PrototypeController controller) {
        super(new BorderLayout());
        JPanel content = Ui.column();
        JPanel explanation = Ui.card();
        Ui.add(explanation, Ui.label("Simulation only", 20, true), 8);
        Ui.add(explanation, Ui.text("These fictional connections demonstrate the controls for a future "
                + "disruption use case. They are not mapped to TTC stations, elevators, or service alerts. "
                + "Changing them updates this interface only."), 0);
        Ui.add(content, explanation, 18);
        for (String connection : state.getDisruptions().keySet()) {
            JPanel card = Ui.card();
            Ui.add(card, Ui.label(connection, 18, true), 8);
            JCheckBox unavailable = new JCheckBox("Mark unavailable in this simulation");
            unavailable.setOpaque(false);
            unavailable.getAccessibleContext().setAccessibleName(connection + ": mark unavailable");
            unavailable.addActionListener(event -> controller.setDisruption(connection, unavailable.isSelected()));
            controls.put(connection, unavailable);
            Ui.add(card, unavailable, 0);
            Ui.add(content, card, 16);
        }
        JButton restore = Ui.button("Restore all sample connections", false);
        restore.addActionListener(event -> controller.restoreConnections());
        Ui.add(content, restore, 16);
        Ui.add(content, notice, 18);
        Ui.add(content, Ui.text("Rerouting and before/after comparison will become available once the routing "
                + "use case and connection mapping are implemented."), 0);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(content, BorderLayout.NORTH);
        add(Ui.scroll(Ui.page("Disruption simulation",
                "Explore how connection controls will work. No live TTC conditions are available.", body)));
        state.addChangeListener(event -> refresh(state));
        refresh(state);
    }

    private void refresh(PrototypeState state) {
        controls.forEach((connection, control) -> control.setSelected(state.getDisruptions().get(connection)));
        Ui.notice(notice, state.getNotice(Screen.DISRUPTIONS), state.isError(Screen.DISRUPTIONS));
    }
}
