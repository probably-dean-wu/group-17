package org.accessgraph.use_case.live_alerts;

import org.accessgraph.entity.Connection;
import org.accessgraph.entity.Disruption;
import org.accessgraph.entity.Route;
import org.accessgraph.entity.ServiceAlert;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.TransitNetwork;
import org.accessgraph.use_case.plan_route.ProfileDataAccessInterface;
import org.accessgraph.use_case.plan_route.RouteFinder;
import org.accessgraph.use_case.plan_route.TransitNetworkDataAccessInterface;
import org.accessgraph.use_case.reroute.DisruptedConnection;
import org.accessgraph.use_case.reroute.DisruptionDataAccessInterface;
import org.accessgraph.use_case.reroute.JourneyRecalculator;
import org.accessgraph.use_case.reroute.RerouteOutputData.Outcome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Brings the transit agency's live no-service alerts into the routing conditions. Each alert in effect
 * closes the supported connections inside its stretch; an alert that matches no supported connection is
 * reported instead of guessed at. Live closures are stored beside the simulated disruptions, so route
 * planning avoids both.
 */
public final class LiveAlertsInteractor implements LiveAlertsInputBoundary {
    private final TransitNetworkDataAccessInterface networkAccess;
    private final ServiceAlertDataAccessInterface alertAccess;
    private final DisruptionDataAccessInterface disruptionAccess;
    private final JourneyRecalculator journeys;
    private final LiveAlertsOutputBoundary presenter;

    /**
     * @param networkAccess supplies the full network, before any disruption is applied
     * @param profileAccess supplies the passenger's current accessibility profile
     * @param alertAccess retrieves the agency's published no-service alerts
     * @param disruptionAccess stores the active disruptions
     * @param routeFinder searches the network that remains once the disruptions are applied
     * @param presenter receives the result
     */
    public LiveAlertsInteractor(TransitNetworkDataAccessInterface networkAccess,
                                ProfileDataAccessInterface profileAccess,
                                ServiceAlertDataAccessInterface alertAccess,
                                DisruptionDataAccessInterface disruptionAccess,
                                RouteFinder routeFinder,
                                LiveAlertsOutputBoundary presenter) {
        this.networkAccess = networkAccess;
        this.alertAccess = alertAccess;
        this.disruptionAccess = disruptionAccess;
        this.journeys = new JourneyRecalculator(profileAccess, disruptionAccess, routeFinder);
        this.presenter = presenter;
    }

    @Override
    public void load(LiveAlertsInputData inputData) {
        List<ServiceAlert> alerts;
        try {
            alerts = alertAccess.getNoServiceAlerts();
        } catch (IOException exception) {
            presenter.prepareFailView("Live TTC alerts were not loaded, so the conditions are unchanged. "
                    + exception.getMessage());
            return;
        }
        TransitNetwork network = networkAccess.getNetwork();
        String originId = inputData.getOriginId();
        String destinationId = inputData.getDestinationId();
        Optional<Route> previous = journeys.findRoute(network, originId, destinationId);

        // The new alerts replace whatever an earlier load applied.
        removeLiveDisruptions();
        List<Disruption> closed = new ArrayList<>();
        Set<String> applied = new LinkedHashSet<>();
        Set<String> unmatched = new LinkedHashSet<>();
        for (ServiceAlert alert : alerts) {
            if (!alert.isActiveAt(inputData.getNow())) {
                continue;
            }
            List<Disruption> closedByAlert = connectionsClosedBy(network, alert);
            if (closedByAlert.isEmpty()) {
                unmatched.add(alert.getDescription());
            } else {
                applied.add(alert.getDescription());
            }
            for (Disruption disruption : closedByAlert) {
                if (closed.stream().noneMatch(disruption::sharesConnectionWith)) {
                    closed.add(disruption);
                }
            }
        }
        // An alert that covers two lines is one notice: if it closed a connection on either, it applied.
        unmatched.removeAll(applied);
        for (Disruption disruption : closed) {
            // A live closure takes the place of a simulated disruption on the same connection.
            for (Disruption active : disruptionAccess.getActiveDisruptions()) {
                if (active.sharesConnectionWith(disruption)) {
                    disruptionAccess.remove(active);
                }
            }
            disruptionAccess.add(disruption);
        }
        Optional<Route> current = journeys.findRoute(network, originId, destinationId);

        Outcome outcome;
        if (!journeys.hasJourney(network, originId, destinationId)) {
            outcome = Outcome.NO_JOURNEY;
        } else if (current.isEmpty()) {
            outcome = previous.isPresent() ? Outcome.NO_ALTERNATIVE : Outcome.NO_ROUTE;
        } else if (previous.isPresent() && closed.stream().anyMatch(closure -> closure.affects(previous.get()))) {
            outcome = Outcome.REROUTED;
        } else {
            outcome = Outcome.UNCHANGED;
        }
        presenter.prepareLoadedView(toOutput(network, inputData, new ArrayList<>(applied),
                new ArrayList<>(unmatched), outcome, current));
    }

    @Override
    public void clear(LiveAlertsInputData inputData) {
        TransitNetwork network = networkAccess.getNetwork();
        List<Disruption> reopened = removeLiveDisruptions();
        Optional<Route> current = journeys.findRoute(network, inputData.getOriginId(),
                inputData.getDestinationId());

        Outcome outcome;
        if (!journeys.hasJourney(network, inputData.getOriginId(), inputData.getDestinationId())) {
            outcome = Outcome.NO_JOURNEY;
        } else if (current.isEmpty()) {
            outcome = Outcome.NO_ROUTE;
        } else if (reopened.stream().anyMatch(closure -> closure.affects(current.get()))) {
            outcome = Outcome.REROUTED;
        } else {
            outcome = Outcome.UNCHANGED;
        }
        presenter.prepareClearedView(toOutput(network, inputData, List.of(), List.of(), outcome, current));
    }

    /** Removes every live disruption from the store and returns the ones that were removed. */
    private List<Disruption> removeLiveDisruptions() {
        List<Disruption> removed = new ArrayList<>();
        for (Disruption disruption : disruptionAccess.getActiveDisruptions()) {
            if (disruption.isLive()) {
                disruptionAccess.remove(disruption);
                removed.add(disruption);
            }
        }
        return removed;
    }

    /** Returns one live disruption for each supported connection inside the alert's stretch. */
    private static List<Disruption> connectionsClosedBy(TransitNetwork network, ServiceAlert alert) {
        List<Disruption> closed = new ArrayList<>();
        for (String stationId : alert.getStationIds()) {
            for (Connection connection : network.connectionsFrom(stationId)) {
                if (alert.closes(connection) && closed.stream().noneMatch(found -> found.affects(connection))) {
                    closed.add(new Disruption(Disruption.Type.OUTAGE, Disruption.Source.LIVE,
                            connection.getLine(), connection.getFromId(), connection.getToId()));
                }
            }
        }
        return closed;
    }

    private LiveAlertsOutputData toOutput(TransitNetwork network, LiveAlertsInputData inputData,
                                          List<String> applied, List<String> unmatched, Outcome outcome,
                                          Optional<Route> route) {
        List<String> stationNames = new ArrayList<>();
        if (route.isPresent()) {
            for (Station station : route.get().getStations()) {
                stationNames.add(station.getName());
            }
        }
        return new LiveAlertsOutputData(inputData.getNow(), applied, unmatched,
                DisruptedConnection.listOf(network, disruptionAccess.getActiveDisruptions()), outcome, stationNames,
                route.map(Route::getTotalMinutes).orElse(0), route.map(Route::getTransfers).orElse(0));
    }
}
