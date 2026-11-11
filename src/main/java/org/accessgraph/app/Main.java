package org.accessgraph.app;

import org.accessgraph.view.AccessGraphFrame;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            System.out.println("AccessGraph Swing interface prototype\nRun: java -jar target/accessgraph.jar\n"
                    + "Requires Java 11+ and a graphical desktop. Routing is not connected.\n"
                    + "Home: destination addresses, accessible station map, and permission-based device location.\n"
                    + "Embedded Google map: Settings → Google Maps → Save key (or set GOOGLE_MAPS_API_KEY).\n"
                    + "Use my location opens a local browser permission page; no location is collected at startup.");
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("AccessGraph requires a graphical desktop to open its Swing window. "
                    + "Run mvn test for headless verification.");
            System.exit(1);
        }
        SwingUtilities.invokeLater(() -> new AccessGraphFrame(AppFactory.createPanel()).setVisible(true));
    }
}
