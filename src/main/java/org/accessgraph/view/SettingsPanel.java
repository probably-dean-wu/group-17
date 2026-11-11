package org.accessgraph.view;

import org.accessgraph.interface_adapter.ApiKeySettings;
import org.accessgraph.interface_adapter.PrototypeController;
import org.accessgraph.interface_adapter.PrototypeState;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

final class SettingsPanel extends JPanel implements AutoCloseable {
    private final JTabbedPane tabs = new JTabbedPane();
    private final MapApiKeyPanel maps;

    SettingsPanel(PrototypeState state, PrototypeController controller, ApiKeySettings settings) {
        super(new BorderLayout());
        maps = new MapApiKeyPanel(settings);
        tabs.getAccessibleContext().setAccessibleName("Settings sections");
        tabs.addTab("Accessibility preferences", new ProfilePanel(state, controller));
        tabs.addTab("Google Maps", maps);
        add(tabs, BorderLayout.CENTER);
    }

    void showMaps() { tabs.setSelectedIndex(1); }
    void showAccessibility() { tabs.setSelectedIndex(0); }
    void initialise() { maps.initialise(); }
    @Override public void close() { maps.close(); }
}
