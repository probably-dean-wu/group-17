package org.accessgraph.view;

import org.accessgraph.interface_adapter.GoogleMapsLinks;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.GeoPosition;
import org.accessgraph.interface_adapter.BrowserGateway;
import org.accessgraph.use_case.station_map.MapImageGateway;
import org.accessgraph.use_case.station_map.MapRequest;
import org.accessgraph.use_case.station_map.LocationGateway;

import javax.imageio.ImageIO;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.event.HierarchyEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/** Map-first journey view. Device location requires permission; all image requests run off Swing's thread. */
public final class StationMapPanel extends JPanel implements AutoCloseable {
    private static final URI TTC_ALERTS = URI.create("https://www.ttc.ca/service-alerts?accessibility");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.CANADA);
    private final List<Station> stations;
    private final MapImageGateway maps;
    private final BrowserGateway browser;
    private final LocationGateway locations;
    private final JTextField destinationAddress = new JTextField();
    private final JTextArea locationNotice = Ui.text("Your location is off. Choose Use my location to share it with permission.");
    private final JButton useLocation = Ui.button("Use my location", false);
    private final JButton stopSharing = Ui.button("Stop sharing", false);
    private final JButton showDestination = Ui.button("Show destination on map", true);
    private final JButton openJourney = Ui.button("Open journey in Google Maps", false);
    private final JTextField search = new JTextField();
    private final JCheckBox confirmedOnly = new JCheckBox("Wheelchair-accessible listings only", true);
    private final DefaultListModel<Station> listModel = new DefaultListModel<>();
    private final JList<Station> list = new JList<>(listModel);
    private final JLabel count = Ui.label("", 14, true);
    private final JTextArea details = Ui.text("");
    private final JTextArea notice = Ui.text("");
    private final JButton showAll = Ui.button("Show listed stations", true);
    private final JButton focus = Ui.button("Focus selected station", false);
    private final JButton google = Ui.button("Open selected in Google Maps", false);
    private final JButton source = Ui.button("View TTC station details", false);
    private final JButton mapSettings = Ui.button("Map settings", false);
    private Runnable openSettings = () -> { };
    private final MapCanvas canvas = new MapCanvas();
    private SwingWorker<BufferedImage, Void> activeWorker;
    private final Timer locationTimer = new Timer(1000, event -> locationTick());
    private GeoPosition position;
    private String appliedDestination = "";
    private boolean tracking;
    private boolean autoRefresh;
    private boolean locationDirty;
    private boolean initialMapRequested;
    private long locationSession;
    private long lastMapRequest;

    public StationMapPanel(List<Station> stations, MapImageGateway maps, BrowserGateway browser,
                           LocationGateway locations) {
        super(new BorderLayout());
        this.stations = List.copyOf(stations);
        this.maps = maps;
        this.browser = browser;
        this.locations = locations;
        JPanel columns = new JPanel(new BorderLayout(20, 0));
        columns.setOpaque(false);
        JTabbedPane inputs = new JTabbedPane();
        inputs.setFont(Ui.BODY.deriveFont(13f));
        inputs.getAccessibleContext().setAccessibleName("Journey and station inputs");
        inputs.addTab("Your journey", buildJourney());
        inputs.addTab("Stations", buildCatalogue());
        inputs.setPreferredSize(new Dimension(350, 650));
        columns.add(inputs, BorderLayout.WEST);
        columns.add(buildMap(), BorderLayout.CENTER);
        add(Ui.scroll(Ui.page("Where are you heading?",
                "Enter a destination and see it alongside accessible TTC stations. Share your location to add your position.",
                columns)), BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { filterStations(); }
            @Override public void removeUpdate(DocumentEvent event) { filterStations(); }
            @Override public void changedUpdate(DocumentEvent event) { filterStations(); }
        });
        confirmedOnly.addActionListener(event -> filterStations());
        list.addListSelectionListener(event -> updateSelection());
        showAll.addActionListener(event -> loadMap(null));
        focus.addActionListener(event -> loadMap(list.getSelectedValue()));
        google.addActionListener(event -> {
            Station station = list.getSelectedValue();
            if (station != null) { openBrowser(GoogleMapsLinks.station(station)); }
        });
        source.addActionListener(event -> {
            Station station = list.getSelectedValue();
            if (station != null) { openBrowser(station.getSource()); }
        });
        destinationAddress.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { destinationEdited(); }
            @Override public void removeUpdate(DocumentEvent event) { destinationEdited(); }
            @Override public void changedUpdate(DocumentEvent event) { destinationEdited(); }
        });
        showDestination.addActionListener(event -> showDestination());
        destinationAddress.addActionListener(event -> showDestination());
        openJourney.addActionListener(event -> openJourney());
        useLocation.addActionListener(event -> startLocation());
        stopSharing.addActionListener(event -> stopLocation("Location sharing stopped. Your position has been cleared."));
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing() && !initialMapRequested) {
                initialMapRequested = true;
                if (maps.isConfigured()) { loadMap(null); }
            }
        });
        filterStations();
    }

    private JPanel buildJourney() {
        JPanel journey = Ui.card();
        Ui.field(journey, "Destination address", destinationAddress);
        destinationAddress.setFont(Ui.BODY);
        destinationAddress.setToolTipText("For example: 100 Queen St W, Toronto. Include the city or postal code.");
        Ui.add(journey, Ui.text("Include the city or postal code, for example 100 Queen St W, Toronto."), 18);
        Ui.add(journey, showDestination, 10);
        Ui.add(journey, openJourney, 14);
        Ui.add(journey, Ui.label("Your location", 17, true), 10);
        locationNotice.getAccessibleContext().setAccessibleName("Your location status");
        Ui.add(journey, locationNotice, 12);
        JPanel locationActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        locationActions.setOpaque(false);
        useLocation.setMargin(new java.awt.Insets(10, 12, 10, 12));
        stopSharing.setMargin(new java.awt.Insets(10, 12, 10, 12));
        locationActions.add(useLocation);
        locationActions.add(javax.swing.Box.createHorizontalStrut(8));
        locationActions.add(stopSharing);
        Ui.add(journey, locationActions, 18);
        Ui.add(journey, Ui.text("A local browser page asks for permission. Keep it open while sharing; "
                + "your device's reported accuracy is shown here. No location history is saved."), 18);
        Ui.add(journey, Ui.text("The map shows station listings and destination pins. AccessGraph has not yet "
                + "calculated an accessible route. Check the Stations tab for TTC details."), 0);
        stopSharing.setEnabled(false);
        return journey;
    }

    private JPanel buildCatalogue() {
        JPanel catalogue = Ui.card();
        Ui.add(catalogue, Ui.label("Station directory", 20, true), 16);
        Ui.field(catalogue, "Search stations", search);
        search.setFont(Ui.BODY);
        confirmedOnly.setOpaque(false);
        confirmedOnly.setFont(Ui.BODY.deriveFont(13f));
        Ui.add(catalogue, confirmedOnly, 12);
        Ui.add(catalogue, count, 10);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(Ui.BODY);
        list.setFixedCellHeight(37);
        list.getAccessibleContext().setAccessibleName("TTC station directory");
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", true);
        list.setCellRenderer((owner, station, index, selected, cellFocus) -> {
            JLabel label = (JLabel) renderer.getListCellRendererComponent(owner, station.getName(),
                    index, selected, cellFocus);
            label.setToolTipText(accessibilityText(station));
            label.getAccessibleContext().setAccessibleName(station.getName() + ": " + accessibilityText(station));
            return label;
        });
        JScrollPane listScroll = Ui.scroll(list);
        listScroll.setPreferredSize(new Dimension(290, 220));
        listScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        Ui.add(catalogue, listScroll, 16);
        Ui.add(catalogue, details, 16);
        Ui.add(catalogue, google, 10);
        Ui.add(catalogue, source, 0);
        return catalogue;
    }

    private JPanel buildMap() {
        JPanel map = Ui.card();
        JPanel title = new JPanel(new BorderLayout(10, 0));
        title.setOpaque(false);
        title.add(Ui.label("Google Maps", 20, true), BorderLayout.WEST);
        mapSettings.setFont(Ui.BODY.deriveFont(13f));
        mapSettings.setMargin(new java.awt.Insets(6, 10, 6, 10));
        mapSettings.setEnabled(false);
        mapSettings.addActionListener(event -> openSettings.run());
        title.add(mapSettings, BorderLayout.EAST);
        Ui.add(map, title, 8);
        Ui.add(map, notice, 14);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(showAll);
        actions.add(javax.swing.Box.createHorizontalStrut(10));
        actions.add(focus);
        Ui.add(map, actions, 14);
        Ui.add(map, Ui.text("Green: TTC lists accessible access. Amber: limited access. "
                + "Grey: unknown or conflicting details. Blue U: your device position. Red D: destination; S: selected station."), 14);
        Ui.add(map, canvas, 14);
        JButton alerts = Ui.button("Check TTC elevator alerts", false);
        alerts.addActionListener(event -> openBrowser(TTC_ALERTS));
        Ui.add(map, alerts, 14);
        String checked = stations.stream().map(Station::getCheckedOn).min(java.time.LocalDate::compareTo)
                .map(DATE::format).orElse("unavailable");
        Ui.add(map, Ui.text("Source: TTC station pages checked " + checked + "; coordinates from TTC GTFS. "
                + "This is a dated station listing, not live elevator status or a verified journey."), 0);
        JPanel policies = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 10));
        policies.setOpaque(false);
        for (String[] link : new String[][]{
                {"Google Maps terms", "https://maps.google.com/help/terms_maps/"},
                {"Google privacy", "https://policies.google.com/privacy"}}) {
            JButton button = Ui.button(link[0], false);
            button.setFont(Ui.BODY.deriveFont(12f));
            button.setMargin(new java.awt.Insets(4, 8, 4, 8));
            button.addActionListener(event -> openBrowser(URI.create(link[1])));
            policies.add(button);
        }
        Ui.add(map, policies, 0);
        return map;
    }

    private void filterStations() {
        cancelLoad();
        autoRefresh = false;
        Station selected = list.getSelectedValue();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        listModel.clear();
        stations.stream().filter(station -> !confirmedOnly.isSelected()
                || station.getAccessibility() == Station.Accessibility.CONFIRMED)
                .filter(station -> station.getName().toLowerCase(Locale.ROOT).contains(query))
                .forEach(listModel::addElement);
        if (selected != null && listModel.contains(selected)) { list.setSelectedValue(selected, true); }
        count.setText(listModel.size() + " of " + stations.size() + " stations listed");
        canvas.clear(listModel.isEmpty() ? "No matching stations" : maps.isConfigured()
                ? "Choose Show listed stations to load the map" : "Google Maps API key required for this view");
        Ui.notice(notice, maps.isConfigured()
                ? "Maps load on request. Filtering clears the previous map so its pins match the list."
                : "Add your API key in Settings → Google Maps to display this map. "
                        + "Destination entry, device location, and Google Maps browser links work without a key.", false);
        updateSelection();
    }

    private void updateSelection() {
        Station selected = list.getSelectedValue();
        google.setEnabled(selected != null);
        source.setEnabled(selected != null);
        showAll.setEnabled(maps.isConfigured() && !listModel.isEmpty() && activeWorker == null);
        focus.setEnabled(maps.isConfigured() && selected != null && activeWorker == null);
        showDestination.setEnabled(maps.isConfigured() && !destinationAddress.getText().isBlank() && activeWorker == null);
        openJourney.setEnabled(!destinationAddress.getText().isBlank());
        details.setText(selected == null
                ? "Select a station for its accessibility listing and browser links."
                : selected.getName() + "\n" + accessibilityText(selected)
                        + "\nChecked " + DATE.format(selected.getCheckedOn()));
    }

    private static String accessibilityText(Station station) {
        switch (station.getAccessibility()) {
            case CONFIRMED: return "TTC lists wheelchair-accessible station access";
            case LIMITED: return "Limited access; subway access is not confirmed step-free";
            default: return "Unknown: TTC details are missing or conflict";
        }
    }

    private List<Station> visibleStations() {
        return java.util.Collections.list(listModel.elements()).stream().collect(Collectors.toList());
    }

    private void destinationEdited() {
        cancelLoad();
        appliedDestination = "";
        autoRefresh = false;
        canvas.clear(maps.isConfigured() ? "Choose Show destination on map" : "Google Maps API key required for this view");
        Ui.notice(notice, maps.isConfigured()
                ? "Destination changed. Choose Show destination on map to update its pin."
                : "Add a key in Settings → Google Maps, or open your destination in Google Maps without a key.",
                false);
        updateSelection();
    }

    private String destination() {
        String address = MapRequest.validateDestination(destinationAddress.getText());
        if (address.isEmpty()) { throw new IllegalArgumentException("Enter a destination address, including its city."); }
        return address;
    }

    private void showDestination() {
        try {
            appliedDestination = destination();
            if (!maps.isConfigured()) {
                Ui.notice(notice, "Add a key in Settings → Google Maps, or choose Open journey in Google Maps.", false);
                return;
            }
            cancelLoad();
            loadMap(null);
        } catch (IllegalArgumentException exception) {
            Ui.notice(notice, exception.getMessage(), true);
        }
    }

    private void openJourney() {
        try {
            openBrowser(GoogleMapsLinks.journey(recentPosition(), destination()));
        } catch (IllegalArgumentException exception) {
            Ui.notice(notice, exception.getMessage(), true);
        }
    }

    private GeoPosition recentPosition() {
        return position != null && position.isRecent(Instant.now()) ? position : null;
    }

    private void startLocation() {
        if (tracking) { return; }
        long session = ++locationSession;
        tracking = true;
        autoRefresh = true;
        useLocation.setEnabled(false);
        stopSharing.setEnabled(true);
        Ui.notice(locationNotice, "Opening a local browser permission page. Choose Start sharing location, then Allow.", false);
        try {
            URI page = locations.start(new LocationGateway.Listener() {
                @Override
                public void onPosition(GeoPosition fix) {
                    SwingUtilities.invokeLater(() -> {
                        if (!tracking || session != locationSession || !fix.isRecent(Instant.now())) { return; }
                        if (position != null && fix.getObservedAt().isBefore(position.getObservedAt())) { return; }
                        boolean firstFix = position == null;
                        boolean moved = firstFix || !position.getCoordinates().equals(fix.getCoordinates());
                        position = fix;
                        locationDirty |= moved;
                        updateLocationNotice();
                        if (firstFix && autoRefresh && maps.isConfigured()) {
                            cancelLoad();
                            loadMap(null);
                        }
                    });
                }

                @Override
                public void onFailure(LocationGateway.Failure failure) {
                    SwingUtilities.invokeLater(() -> {
                        if (!tracking || session != locationSession) { return; }
                        if (failure == LocationGateway.Failure.PERMISSION_DENIED) {
                            stopLocation("Location permission denied. Allow it in your browser settings and try again.");
                        } else if (failure == LocationGateway.Failure.STOPPED) {
                            stopLocation("Location sharing stopped in your browser. Your position has been cleared.");
                        } else {
                            position = null;
                            cancelLoad();
                            canvas.clear("Waiting for a fresh device location");
                            Ui.notice(locationNotice, "A fresh device location is unavailable. Waiting for an update; "
                                    + "you can stop sharing and try again.", false);
                        }
                    });
                }
            });
            browser.open(page);
            locationTimer.start();
        } catch (IOException exception) {
            stopLocation("Could not open the local location-permission page. Check your default browser and try again.");
        }
    }

    private void updateLocationNotice() {
        if (position == null) { return; }
        long age = Math.max(0, Duration.between(position.getObservedAt(), Instant.now()).getSeconds());
        String message = String.format(Locale.CANADA, "%.5f, %.5f\nDevice accuracy: ±%.0f m\nUpdated %s (%d s ago)",
                position.getLatitude(), position.getLongitude(), position.getAccuracyMetres(),
                localTime(position.getObservedAt()), age);
        Ui.notice(locationNotice, message, false);
    }

    private static String localTime(Instant value) {
        return DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(value);
    }

    private void locationTick() {
        if (!tracking) { return; }
        if (position != null && recentPosition() == null) {
            position = null;
            cancelLoad();
            canvas.clear("Device location expired; waiting for an update");
            Ui.notice(locationNotice, "Last position expired after 60 seconds. Waiting for a fresh device location.", false);
        }
        updateLocationNotice();
        if (autoRefresh && locationDirty && recentPosition() != null && activeWorker == null && maps.isConfigured()
                && System.nanoTime() - lastMapRequest >= java.util.concurrent.TimeUnit.SECONDS.toNanos(30)) {
            loadMap(null);
        }
    }

    private void stopLocation(String message) {
        ++locationSession;
        tracking = false;
        autoRefresh = false;
        locationDirty = false;
        position = null;
        locationTimer.stop();
        locations.stop();
        cancelLoad();
        canvas.clear("Location cleared. Choose a map action to reload station pins.");
        useLocation.setEnabled(true);
        stopSharing.setEnabled(false);
        Ui.notice(locationNotice, message, false);
        updateSelection();
    }

    @Override
    public void close() { stopLocation("Location sharing ended."); }

    public void setOpenSettingsAction(Runnable action) {
        openSettings = java.util.Objects.requireNonNull(action);
        mapSettings.setEnabled(true);
    }

    /** Called on Swing's event dispatch thread when a key is replaced or removed. */
    public void configurationChanged() {
        cancelLoad();
        autoRefresh = false;
        initialMapRequested = false;
        canvas.clear(maps.isConfigured() ? "Map configuration updated" : "Add an API key in Map settings");
        Ui.notice(notice, maps.isConfigured()
                ? "Google Maps key updated. Open Plan journey or choose a map action to load the map."
                : "No Google Maps key is configured. Choose Map settings to add one.", false);
        updateSelection();
        if (maps.isConfigured() && isShowing()) {
            initialMapRequested = true;
            loadMap(null);
        }
    }

    private void loadMap(Station selected) {
        if (activeWorker != null || !maps.isConfigured()) { return; }
        List<Station> visible = List.copyOf(visibleStations());
        GeoPosition currentPosition = recentPosition();
        if (visible.isEmpty() && currentPosition == null && appliedDestination.isEmpty()) { return; }
        MapRequest request = new MapRequest(visible, selected, currentPosition, appliedDestination);
        autoRefresh = selected == null && tracking;
        locationDirty = false;
        lastMapRequest = System.nanoTime();
        canvas.clear("Loading Google Maps…");
        Ui.notice(notice, "Loading " + visible.size() + " station pins…", false);
        activeWorker = new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws IOException, InterruptedException {
                byte[] bytes = maps.fetch(request);
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) { throw new IOException("Google Maps did not return a readable map image."); }
                return image;
            }

            @Override
            protected void done() {
                if (activeWorker != this) { return; }
                activeWorker = null;
                try {
                    canvas.showImage(get());
                    String message = selected == null ? "Showing all " + visible.size() + " listed station pins."
                            : "Focused on " + selected.getName() + ". Other pins may be outside this view.";
                    if (!request.getDestination().isEmpty()) {
                        message += " D is Google's match for “" + request.getDestination() + "”; check its location.";
                    }
                    if (request.getPosition() != null) {
                        message += " U uses the device fix from " + localTime(request.getPosition().getObservedAt())
                                + ". While sharing, changed positions refresh at most every 30 seconds.";
                    }
                    Ui.notice(notice, message, false);
                } catch (CancellationException exception) {
                    canvas.clear("Map load cancelled");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    canvas.clear("Map load interrupted");
                } catch (ExecutionException exception) {
                    canvas.clear("Map unavailable");
                    Throwable cause = exception.getCause();
                    Ui.notice(notice, cause instanceof IOException ? cause.getMessage()
                            : "The map could not be loaded. Try again or open the selected station in Google Maps.", true);
                }
                updateSelection();
                revalidate();
            }
        };
        updateSelection();
        activeWorker.execute();
    }

    private void cancelLoad() {
        if (activeWorker != null) {
            activeWorker.cancel(true);
            activeWorker = null;
        }
    }

    private void openBrowser(URI uri) {
        try {
            browser.open(uri);
            Ui.notice(notice, "Opened in your default browser.", false);
        } catch (IOException exception) {
            Ui.notice(notice, "Could not open a browser. Open this address manually: " + uri, true);
        }
    }

    /** Draw the complete image, retaining Google's logo and attribution without overlays or cropping. */
    private static final class MapCanvas extends JPanel {
        private BufferedImage image;
        private String message = "Google Maps has not been loaded";

        MapCanvas() {
            setPreferredSize(new Dimension(640, 400));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
            setBackground(Ui.BACKGROUND);
            getAccessibleContext().setAccessibleName("Google station map");
        }

        void clear(String text) {
            image = null;
            message = text;
            getAccessibleContext().setAccessibleDescription(text + ". Station information is also in the directory.");
            repaint();
        }

        void showImage(BufferedImage value) {
            image = value;
            getAccessibleContext().setAccessibleDescription("Google Maps station pins. "
                    + "Use the station directory for a text alternative and station details.");
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D copy = (Graphics2D) graphics.create();
            if (image == null) {
                copy.setColor(Ui.MUTED);
                copy.setFont(Ui.BODY.deriveFont(13f));
                copy.drawString(message, 15, getHeight() / 2);
            } else {
                double scale = Math.min(1, Math.min((double) getWidth() / image.getWidth(),
                        (double) getHeight() / image.getHeight()));
                int width = Math.max(1, (int) (image.getWidth() * scale));
                int height = Math.max(1, (int) (image.getHeight() * scale));
                copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                copy.drawImage(image, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, null);
            }
            copy.dispose();
        }
    }
}
