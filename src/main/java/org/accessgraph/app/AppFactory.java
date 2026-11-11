package org.accessgraph.app;

import org.accessgraph.interface_adapter.PrototypeController;
import org.accessgraph.interface_adapter.PrototypeState;
import org.accessgraph.view.AccessGraphPanel;
import org.accessgraph.view.StationMapPanel;
import org.accessgraph.data_access.BundledStationRepository;
import org.accessgraph.data_access.BrowserLocationGateway;
import org.accessgraph.data_access.DesktopBrowser;
import org.accessgraph.data_access.GoogleStaticMapsGateway;
import org.accessgraph.data_access.MacKeychainApiKeyStore;
import org.accessgraph.interface_adapter.ApiKeySettings;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

/** Composition root. Future gateways and interactors will be wired here. */
public final class AppFactory {
    private AppFactory() { }

    public static AccessGraphPanel createPanel() {
        PrototypeState state = new PrototypeState();
        PrototypeController controller = new PrototypeController(state);
        ApiKeySettings settings = new ApiKeySettings(System.getenv("GOOGLE_MAPS_API_KEY"), new MacKeychainApiKeyStore());
        JComponent stationMap;
        try {
            stationMap = new StationMapPanel(new BundledStationRepository().load(),
                    new GoogleStaticMapsGateway(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                            settings::keyForRequest), new DesktopBrowser(), new BrowserLocationGateway());
        } catch (IOException exception) {
            JPanel error = new JPanel(new BorderLayout());
            error.add(new JLabel("Station catalogue unavailable. Rebuild the application with its bundled resources."),
                    BorderLayout.NORTH);
            stationMap = error;
        }
        AccessGraphPanel panel = new AccessGraphPanel(state, controller, stationMap, settings);
        SwingUtilities.invokeLater(panel::initialiseSettings);
        return panel;
    }
}
