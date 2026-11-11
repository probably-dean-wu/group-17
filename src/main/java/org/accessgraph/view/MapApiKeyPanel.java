package org.accessgraph.view;

import org.accessgraph.interface_adapter.ApiKeySettings;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.HierarchyEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/** Masked credential editor. Existing credentials are never copied back into the input field. */
final class MapApiKeyPanel extends JPanel implements AutoCloseable {
    private final ApiKeySettings settings;
    private final JPasswordField key = new JPasswordField();
    private final JCheckBox showKey = new JCheckBox("Show key");
    private final JCheckBox remember = new JCheckBox("Remember in macOS Keychain");
    private final JButton save = Ui.button("Save key", true);
    private final JButton remove = Ui.button("Remove key", false);
    private final JTextArea status = Ui.text("");
    private SwingWorker<Void, Void> worker;
    private boolean initialised;

    MapApiKeyPanel(ApiKeySettings settings) {
        super(new BorderLayout());
        this.settings = settings;
        JPanel card = Ui.card();
        Ui.add(card, Ui.label("Google Maps connection", 20, true), 12);
        Ui.add(card, Ui.text("Paste your Maps Static API key here. Saving applies it immediately; "
                + "you do not need a terminal or an application restart."), 18);
        key.setFont(Ui.BODY);
        key.setEchoChar('•');
        Ui.field(card, "Google Maps API key", key);
        showKey.setOpaque(false);
        showKey.setFont(Ui.BODY);
        showKey.addActionListener(event -> key.setEchoChar(showKey.isSelected() ? (char) 0 : '•'));
        Ui.add(card, showKey, 12);
        remember.setOpaque(false);
        remember.setFont(Ui.BODY);
        remember.setSelected(settings.canRemember());
        remember.setEnabled(settings.canRemember());
        Ui.add(card, remember, 8);
        Ui.add(card, Ui.text(settings.canRemember()
                ? "Remembered keys are stored in your Mac's Keychain. Unchecked keys apply only to this session; "
                        + "an older remembered key stays saved until you choose Remove key."
                : "Secure remembering is unavailable on this platform. Keys entered here are used for this session only."), 18);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(save);
        actions.add(javax.swing.Box.createHorizontalStrut(12));
        actions.add(remove);
        Ui.add(card, actions, 18);
        status.getAccessibleContext().setAccessibleName("Google Maps configuration status");
        Ui.add(card, status, 18);
        Ui.add(card, Ui.text("Enable Maps Static API and billing in your Google Cloud project. "
                + "Google checks the key when a map loads. The input is hidden again after saving or leaving this screen."), 0);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(card, BorderLayout.NORTH);
        add(Ui.scroll(Ui.page("Google Maps settings", "Your key stays out of the source code, map labels, and status messages.", body)));
        save.addActionListener(event -> saveKey());
        key.addActionListener(event -> { if (save.isEnabled()) { saveKey(); } });
        remove.addActionListener(event -> runTask(settings::clear, "Removing key…",
                "Key removed. Maps are disabled for this session. An environment key, if set, returns at the next launch.",
                "Maps are disabled for this session, but the remembered key could not be removed. Unlock macOS Keychain and try again."));
        key.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { updateControls(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { updateControls(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { updateControls(); }
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) { hideKey(); }
        });
        settings.addChangeListener(event -> SwingUtilities.invokeLater(() -> {
            if (worker == null) { Ui.notice(status, settings.description(), false); }
            updateControls();
        }));
        Ui.notice(status, settings.description(), false);
        updateControls();
    }

    void initialise() {
        if (initialised) { return; }
        initialised = true;
        runTask(settings::loadRemembered, "Checking for a remembered key…", null,
                "Could not read macOS Keychain. You can unlock it and restart, or enter a key for this session.");
    }

    private void saveKey() {
        char[] value = key.getPassword();
        boolean persist = remember.isSelected();
        runTask(() -> {
            try { settings.save(value, persist); }
            finally { Arrays.fill(value, '\0'); }
        }, "Saving key…", "Key saved. " + (persist ? "Remembered securely in macOS Keychain." : "Using it for this session only."),
                "Could not save the key. Unlock macOS Keychain or uncheck Remember to use a session-only key.");
    }

    private void runTask(Task task, String working, String success, String failure) {
        if (worker != null) { return; }
        hideKey();
        Ui.notice(status, working, false);
        worker = new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws IOException { task.run(); return null; }
            @Override protected void done() {
                worker = null;
                try {
                    get();
                    key.setText("");
                    Ui.notice(status, success == null ? settings.description() : success, false);
                } catch (CancellationException exception) {
                    Ui.notice(status, "Key operation cancelled.", false);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    Ui.notice(status, "Key operation interrupted.", true);
                } catch (ExecutionException exception) {
                    Ui.notice(status, exception.getCause() instanceof IllegalArgumentException
                            ? "Paste a key with 10–200 letters, digits, - or _." : failure, true);
                }
                hideKey();
                updateControls();
            }
        };
        updateControls();
        worker.execute();
    }

    private void updateControls() {
        boolean busy = worker != null;
        char[] value = key.getPassword();
        save.setEnabled(!busy && value.length > 0);
        Arrays.fill(value, '\0');
        key.setEnabled(!busy);
        showKey.setEnabled(!busy);
        remember.setEnabled(!busy && settings.canRemember());
        remove.setEnabled(!busy && (settings.isConfigured() || settings.canRemember()));
    }

    private void hideKey() { showKey.setSelected(false); key.setEchoChar('•'); }

    @Override
    public void close() {
        if (worker != null) { worker.cancel(true); }
        key.setText("");
        hideKey();
    }

    @FunctionalInterface
    private interface Task { void run() throws IOException; }
}
