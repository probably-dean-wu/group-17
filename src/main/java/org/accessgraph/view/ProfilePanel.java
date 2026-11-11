package org.accessgraph.view;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.interface_adapter.PrototypeController;
import org.accessgraph.interface_adapter.PrototypeState;
import org.accessgraph.interface_adapter.PrototypeState.Screen;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.text.ParseException;

final class ProfilePanel extends JPanel {
    private final JCheckBox wheelchair = new JCheckBox("Require wheelchair-accessible connections");
    private final JCheckBox stairs = new JCheckBox("Allow connections that require stairs");
    private final JCheckBox escalators = new JCheckBox("Allow connections that require escalators");
    private final JSpinner transfers = new JSpinner(new SpinnerNumberModel(2, 0, 5, 1));
    private final JTextArea notice = Ui.text("");
    private AccessibilityProfile displayedProfile;

    ProfilePanel(PrototypeState state, PrototypeController controller) {
        super(new BorderLayout());
        JPanel card = Ui.card();
        Ui.add(card, Ui.label("Travel requirements", 20, true), 12);
        Ui.add(card, Ui.text("Choose what a future route must support. These settings do not confirm "
                + "that any station or journey is accessible."), 20);
        for (JCheckBox check : new JCheckBox[]{wheelchair, stairs, escalators}) {
            check.setOpaque(false);
            Ui.add(card, check, 14);
        }
        wheelchair.addActionListener(event -> updateMobilityControls());
        Ui.add(card, Ui.text("When wheelchair access is required, connections requiring stairs or escalators "
                + "are excluded."), 20);
        Ui.field(card, "Maximum transfers (0–5)", transfers);
        JButton apply = Ui.button("Apply preferences", true);
        apply.setMnemonic('A');
        apply.addActionListener(event -> {
            try {
                String input = ((JSpinner.DefaultEditor) transfers.getEditor()).getTextField().getText().trim();
                if (!input.matches("[0-5]")) {
                    throw new IllegalArgumentException("Invalid transfer limit.");
                }
                transfers.commitEdit();
                controller.applyProfile(new AccessibilityProfile(wheelchair.isSelected(), stairs.isSelected(),
                        escalators.isSelected(), ((Number) transfers.getValue()).intValue()));
            } catch (ParseException | IllegalArgumentException exception) {
                Ui.notice(notice, "Enter a whole number of transfers between 0 and 5.", true);
                transfers.requestFocusInWindow();
            }
        });
        Ui.add(card, apply, 12);
        JButton reset = Ui.button("Restore default preferences", false);
        reset.addActionListener(event -> controller.resetProfile());
        Ui.add(card, reset, 18);
        Ui.add(card, notice, 12);
        Ui.add(card, Ui.text("Local file storage will be added later. Closing the app clears your preferences."), 0);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(card, BorderLayout.NORTH);
        add(Ui.scroll(Ui.page("Accessibility preferences",
                "Set your needs before reviewing a journey. Apply changes to use them elsewhere in the app.", body)));
        state.addChangeListener(event -> refresh(state));
        refresh(state);
    }

    private void refresh(PrototypeState state) {
        if (displayedProfile != state.getProfile()) {
            displayedProfile = state.getProfile();
            wheelchair.setSelected(displayedProfile.isWheelchairRequired());
            stairs.setSelected(displayedProfile.isStairsAllowed());
            escalators.setSelected(displayedProfile.isEscalatorsAllowed());
            transfers.setValue(displayedProfile.getMaxTransfers());
            updateMobilityControls();
        }
        Ui.notice(notice, state.getNotice(Screen.PROFILE), state.isError(Screen.PROFILE));
    }

    private void updateMobilityControls() {
        if (wheelchair.isSelected()) {
            stairs.setSelected(false);
            escalators.setSelected(false);
        }
        stairs.setEnabled(!wheelchair.isSelected());
        escalators.setEnabled(!wheelchair.isSelected());
    }
}
