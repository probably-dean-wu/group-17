package org.accessgraph.data_access;

import org.accessgraph.interface_adapter.BrowserGateway;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public final class DesktopBrowser implements BrowserGateway {
    @Override
    public void open(URI uri) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("No default browser is available on this desktop.");
        }
        try {
            Desktop.getDesktop().browse(uri);
        } catch (UnsupportedOperationException | SecurityException exception) {
            throw new IOException("This desktop could not open the browser.");
        }
    }
}
