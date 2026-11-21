package org.accessgraph.view;

import org.accessgraph.interface_adapter.manage_profile.ManageProfileController;
import org.accessgraph.interface_adapter.manage_profile.ManageProfileState;
import org.accessgraph.interface_adapter.manage_profile.ManageProfileViewModel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** Lets the passenger choose and save their accessibility needs, and explains what they mean for routes. */
public final class ProfilePanel extends JPanel {
    private final JCheckBox wheelchair = checkBox("I use a wheelchair (elevators and ramps only)");
    private final JCheckBox stairs = checkBox("Stairs are OK");
    private final JCheckBox escalators = checkBox("Escalators are OK");
    private final JComboBox<Integer> transfers = new JComboBox<>();
    private final JTextArea message = new JTextArea();

    /** @param maxTransfersLimit the largest transfer limit offered in the drop-down */
    public ProfilePanel(ManageProfileController controller, ManageProfileViewModel viewModel,
                        int maxTransfersLimit) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.PAPER);
        setPreferredSize(new Dimension(320, 0));
        for (int limit = 0; limit <= maxTransfersLimit; limit++) {
            transfers.addItem(limit);
        }
        wheelchair.addActionListener(event -> matchWheelchair());
        JButton save = Ui.button("Save profile", true);
        save.addActionListener(event -> controller.save(wheelchair.isSelected(), stairs.isSelected(),
                escalators.isSelected(), (Integer) transfers.getSelectedItem()));

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(left(Ui.label("Your accessibility profile", 18, true)));
        form.add(left(wheelchair));
        form.add(left(stairs));
        form.add(left(escalators));
        form.add(left(Ui.label("Maximum transfers", 13, false)));
        form.add(left(transfers));
        form.add(left(save));

        message.setEditable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setFont(Ui.font(13, false));
        add(form, BorderLayout.NORTH);
        add(new JScrollPane(message), BorderLayout.CENTER);

        viewModel.addPropertyChangeListener(event -> show(viewModel.getState()));
        show(viewModel.getState());
    }

    private void show(ManageProfileState state) {
        wheelchair.setSelected(state.isWheelchairRequired());
        stairs.setSelected(state.isStairsAllowed());
        escalators.setSelected(state.isEscalatorsAllowed());
        transfers.setSelectedItem(state.getMaxTransfers());
        matchWheelchair();
        if (!state.getError().isEmpty()) {
            message.setForeground(Ui.ERROR);
            message.setText(state.getError());
        } else {
            List<String> lines = new ArrayList<>();
            if (!state.getStatus().isEmpty()) {
                lines.add(state.getStatus());
                lines.add("");
            }
            lines.addAll(state.getDetails());
            message.setForeground(Ui.INK);
            message.setText(String.join("\n", lines));
        }
        message.setCaretPosition(0);
    }

    /** A wheelchair profile cannot allow stairs or escalators, so those choices are cleared and disabled. */
    private void matchWheelchair() {
        boolean free = !wheelchair.isSelected();
        if (!free) {
            stairs.setSelected(false);
            escalators.setSelected(false);
        }
        stairs.setEnabled(free);
        escalators.setEnabled(free);
    }

    private static JCheckBox checkBox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setFont(Ui.font(13, false));
        box.setForeground(Ui.INK);
        return box;
    }

    private static Component left(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }
}
