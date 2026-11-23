package org.accessgraph.app;

import org.accessgraph.data_access.BrowserLocationGateway;
import org.accessgraph.data_access.BundledStationRepository;
import org.accessgraph.data_access.DemoAccount;
import org.accessgraph.data_access.DesktopBrowser;
import org.accessgraph.data_access.DisruptionAwareNetworkDataAccess;
import org.accessgraph.data_access.FileProfileDataAccess;
import org.accessgraph.data_access.FileTransitNetworkDataAccess;
import org.accessgraph.data_access.InMemoryDisruptionDataAccess;
import org.accessgraph.data_access.InMemoryProfileDataAccess;
import org.accessgraph.data_access.TtcAlertGateway;
import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.data_access.OpenStreetMapGateway;
import org.accessgraph.entity.Connection;
import org.accessgraph.entity.Disruption;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.TransitNetwork;
import org.accessgraph.interface_adapter.manage_profile.ManageProfileController;
import org.accessgraph.interface_adapter.manage_profile.ManageProfilePresenter;
import org.accessgraph.interface_adapter.manage_profile.ManageProfileViewModel;
import org.accessgraph.interface_adapter.plan_route.PlanRouteController;
import org.accessgraph.interface_adapter.plan_route.PlanRoutePresenter;
import org.accessgraph.interface_adapter.plan_route.PlanRouteViewModel;
import org.accessgraph.interface_adapter.reroute.JourneyRecorder;
import org.accessgraph.interface_adapter.reroute.LiveAlertsController;
import org.accessgraph.interface_adapter.reroute.LiveAlertsPresenter;
import org.accessgraph.interface_adapter.reroute.RerouteController;
import org.accessgraph.interface_adapter.reroute.ReroutePresenter;
import org.accessgraph.interface_adapter.reroute.RerouteViewModel;
import org.accessgraph.use_case.live_alerts.LiveAlertsInteractor;
import org.accessgraph.use_case.manage_profile.ManageProfileInteractor;
import org.accessgraph.use_case.plan_route.DijkstraRouteFinder;
import org.accessgraph.use_case.plan_route.PlanRouteInteractor;
import org.accessgraph.use_case.reroute.RerouteInteractor;
import org.accessgraph.use_case.sign_in.SignIn;
import org.accessgraph.use_case.station_map.MapSession;
import org.accessgraph.view.AccessGraphPanel;
import org.accessgraph.view.DisruptionPanel;
import org.accessgraph.view.JourneyTabs;
import org.accessgraph.view.PlanRoutePanel;
import org.accessgraph.view.ProfilePanel;

import javax.swing.JComponent;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


import org.accessgraph.data_access.FileSavedTripDataAccess;

import org.accessgraph.interface_adapter.savedTrip.SavedTripsController;
import org.accessgraph.interface_adapter.savedTrip.SavedTripsPresenter;
import org.accessgraph.interface_adapter.savedTrip.SavedTripsViewModel;

import org.accessgraph.use_case.savedTrip.SavedTripsInteractor;

import org.accessgraph.view.SavedTripsPanel;

import java.nio.file.Path;
import java.nio.file.Paths;


/** The composition root connects the use cases and their desktop adapters. */
public final class AppFactory {
    private AppFactory() { }

    public static AccessGraphPanel createPanel() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        return new AccessGraphPanel(new SignIn(new DemoAccount()), DemoAccount.USERNAME, DemoAccount.PASSWORD,
                new MapSession(loadStations()), new OpenStreetMapGateway(http), new BrowserLocationGateway(),
                new DesktopBrowser(), Clock.systemUTC(), createJourneyTabs(http));
    }

    /**
     * Builds the column beside the map. Each use case is wired as
     * view -> controller -> interactor -> presenter -> view model. Route planning and rerouting share one
     * network, one profile and one set of active disruptions, so a simulated disruption or a live alert
     * changes what the planner can find.
     */
    private static JComponent createJourneyTabs(HttpClient http) {
        try {
            FileTransitNetworkDataAccess networkAccess =
                    new FileTransitNetworkDataAccess(new BundledStationRepository().load());
            FileProfileDataAccess profileAccess = new FileProfileDataAccess(AccessibilityProfile.strictest());
            InMemoryDisruptionDataAccess disruptionAccess = new InMemoryDisruptionDataAccess();
            DijkstraRouteFinder routeFinder = new DijkstraRouteFinder();

            PlanRouteViewModel viewModel = new PlanRouteViewModel();
            PlanRoutePresenter presenter = new PlanRoutePresenter(viewModel);

            DisruptionAwareNetworkDataAccess currentNetwork =
                    new DisruptionAwareNetworkDataAccess(
                            networkAccess, disruptionAccess);

            PlanRouteInteractor interactor =
                    new PlanRouteInteractor(
                            currentNetwork,
                            profileAccess,
                            routeFinder,
                            presenter);

            JourneyRecorder journeys = new JourneyRecorder(interactor);
            List<PlanRoutePanel.StationChoice> choices = new ArrayList<>();
            for (Station station : networkAccess.getNetwork().getConnectedStations()) {
                choices.add(new PlanRoutePanel.StationChoice(station.getId(), station.getName()));
            }

            PlanRouteController routeController =
                    new PlanRouteController(journeys);

            PlanRoutePanel routePlanner =
                    new PlanRoutePanel(
                            choices, routeController, viewModel);


            RerouteViewModel rerouteViewModel = new RerouteViewModel();
            RerouteInteractor reroute = new RerouteInteractor(networkAccess, profileAccess, disruptionAccess,
                    routeFinder, new ReroutePresenter(rerouteViewModel));
            LiveAlertsInteractor liveAlerts = new LiveAlertsInteractor(networkAccess, profileAccess,
                    new TtcAlertGateway(http), disruptionAccess, routeFinder,
                    new LiveAlertsPresenter(rerouteViewModel, ZoneId.systemDefault()));
            DisruptionPanel disruptions = new DisruptionPanel(connectionChoices(networkAccess.getNetwork()),
                    typeChoices(), new RerouteController(reroute, journeys),
                    new LiveAlertsController(liveAlerts, journeys, Clock.systemUTC()), rerouteViewModel);

            Path savedTripsFile = Paths.get(
                    System.getProperty("user.home"),
                    ".accessgraph",
                    "saved-trips.properties");

            SavedTripsViewModel savedViewModel =
                    new SavedTripsViewModel();

            SavedTripsInteractor savedInteractor =
                    new SavedTripsInteractor(
                            new FileSavedTripDataAccess(savedTripsFile),
                            currentNetwork,
                            profileAccess,
                            routeFinder,
                            new SavedTripsPresenter(savedViewModel));

            SavedTripsController savedController =
                    new SavedTripsController(savedInteractor);

            SavedTripsPanel savedTripsPanel =
                    new SavedTripsPanel(
                            savedController,
                            savedViewModel,
                            routePlanner,
                            routeController);

            rerouteViewModel.addPropertyChangeListener(
                    event -> savedController.refreshLoadedTrip());

            JourneyTabs tabs = new JourneyTabs(
                    routePlanner,
                    disruptions,
                    savedTripsPanel,
                    rerouteViewModel);

            ManageProfileViewModel profileViewModel = new ManageProfileViewModel();
            ManageProfileController profileController = new ManageProfileController(
                    new ManageProfileInteractor(profileAccess, new ManageProfilePresenter(profileViewModel)),
                    journeys);
            tabs.addProfileTab(new ProfilePanel(profileController, profileViewModel,
                    AccessibilityProfile.MAX_TRANSFERS), profileViewModel);
            // Restore the saved profile before the first search. If there is none, the Profile tab opens.
            profileController.load();
            return tabs;
        } catch (IOException exception) {
            System.err.println("The route planner could not load its network: " + exception.getMessage());
            return null;
        }
    }

    /** Lists every connection once, whichever direction it is travelled in, sorted by line and then name. */
    private static List<DisruptionPanel.ConnectionChoice> connectionChoices(TransitNetwork network) {
        List<DisruptionPanel.ConnectionChoice> choices = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        for (Station station : network.getConnectedStations()) {
            for (Connection connection : network.connectionsFrom(station.getId())) {
                String line = connection.getLine();
                if (listed.add(line + "|" + connection.getFromId() + "|" + connection.getToId())) {
                    listed.add(line + "|" + connection.getToId() + "|" + connection.getFromId());
                    choices.add(new DisruptionPanel.ConnectionChoice(line, connection.getFromId(),
                            connection.getToId(), station.getName() + " ↔ "
                            + network.getStation(connection.getToId()).getName()));
                }
            }
        }
        choices.sort(Comparator.comparing(DisruptionPanel.ConnectionChoice::getLine)
                .thenComparing(DisruptionPanel.ConnectionChoice::toString));
        return choices;
    }

    private static List<DisruptionPanel.TypeChoice> typeChoices() {
        List<DisruptionPanel.TypeChoice> choices = new ArrayList<>();
        for (Disruption.Type type : Disruption.Type.values()) {
            choices.add(new DisruptionPanel.TypeChoice(type.name(), type.getLabel()));
        }
        return choices;
    }

    private static List<Station> loadStations() {
        try {
            return new BundledStationRepository().load().stream()
                    .filter(station -> station.getAccessibility() == Station.Accessibility.CONFIRMED)
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            System.err.println("The bundled TTC catalogue could not be read: " + exception.getMessage());
            return List.of();
        }
    }
}
