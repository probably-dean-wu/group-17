package org.accessgraph.use_case.reroute;

import org.accessgraph.entity.Connection;
import org.accessgraph.entity.Disruption;
import org.accessgraph.entity.Route;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.TransitNetwork;
import org.accessgraph.use_case.plan_route.ProfileDataAccessInterface;
import org.accessgraph.use_case.plan_route.RouteFinder;
import org.accessgraph.use_case.plan_route.TransitNetworkDataAccessInterface;
import org.accessgraph.use_case.reroute.RerouteOutputData.Outcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simulates and restores disruptions on single connections. After each change it recalculates the
 * passenger's journey and reports whether the previous route is still valid, what replaces it, or that
 * no alternative exists.
 */
public final class RerouteInteractor implements RerouteInputBoundary {
    private final TransitNetworkDataAccessInterface networkAccess;
    private final DisruptionDataAccessInterface disruptionAccess;
    private final JourneyRecalculator journeys;
    private final RerouteOutputBoundary presenter;

    /**
     * @param networkAccess supplies the full network, before any disruption is applied
     * @param profileAccess supplies the passenger's current accessibility profile
     * @param disruptionAccess stores the active disruptions
     * @param routeFinder searches the network that remains once the disruptions are applied
     * @param presenter receives the result
     */
    public RerouteInteractor(TransitNetworkDataAccessInterface networkAccess,
                             ProfileDataAccessInterface profileAccess,
                             DisruptionDataAccessInterface disruptionAccess,
                             RouteFinder routeFinder,
                             RerouteOutputBoundary presenter) {
        this.networkAccess = networkAccess;
        this.disruptionAccess = disruptionAccess;
        this.journeys = new JourneyRecalculator(profileAccess, disruptionAccess, routeFinder);
        this.presenter = presenter;
    }

    @Override
    public void simulate(RerouteInputData inputData) {
        TransitNetwork network = networkAccess.getNetwork();
        if (!isSupported(network, inputData)) {
            presenter.prepareFailView("That connection is not part of the supported network.");
            return;
        }
        if (inputData.getType() == null) {
            presenter.prepareFailView("Choose the type of disruption to simulate.");
            return;
        }
        Disruption active = findActive(inputData);
        if (active != null) {
            presenter.prepareFailView(where(network, active) + " is already disrupted.");
            return;
        }

        Disruption disruption = new Disruption(inputData.getType(), inputData.getLine(),
                inputData.getStationId(), inputData.getOtherStationId());
        String originId = inputData.getOriginId();
        String destinationId = inputData.getDestinationId();
        Optional<Route> previous = journeys.findRoute(network, originId, destinationId);
        disruptionAccess.add(disruption);
        Optional<Route> current = journeys.findRoute(network, originId, destinationId);

        Outcome outcome;
        if (!journeys.hasJourney(network, originId, destinationId)) {
            outcome = Outcome.NO_JOURNEY;
        } else if (previous.isEmpty()) {
            outcome = Outcome.NO_ROUTE;
        } else if (!disruption.affects(previous.get())) {
            outcome = Outcome.UNCHANGED;
        } else {
            outcome = current.isPresent() ? Outcome.REROUTED : Outcome.NO_ALTERNATIVE;
        }
        presenter.prepareDisruptedView(toOutput(network, disruption, outcome, current));
    }

    @Override
    public void restore(RerouteInputData inputData) {
        TransitNetwork network = networkAccess.getNetwork();
        Disruption disruption = findActive(inputData);
        if (disruption == null) {
            presenter.prepareFailView("That connection is not disrupted.");
            return;
        }
        if (disruption.isLive()) {
            presenter.prepareFailView(where(network, disruption) + " is closed by a live TTC alert, so it cannot "
                    + "be restored here. Clear the live alerts to plan without them.");
            return;
        }

        String originId = inputData.getOriginId();
        String destinationId = inputData.getDestinationId();
        Optional<Route> previous = journeys.findRoute(network, originId, destinationId);
        disruptionAccess.remove(disruption);
        Optional<Route> current = journeys.findRoute(network, originId, destinationId);

        Outcome outcome;
        if (!journeys.hasJourney(network, originId, destinationId)) {
            outcome = Outcome.NO_JOURNEY;
        } else if (current.isEmpty()) {
            outcome = Outcome.NO_ROUTE;
        } else if (previous.isPresent() && !disruption.affects(current.get())) {
            outcome = Outcome.UNCHANGED;
        } else {
            outcome = Outcome.REROUTED;
        }
        presenter.prepareRestoredView(toOutput(network, disruption, outcome, current));
    }

    /** Whether the full network has a ride on the chosen line directly between the two chosen stations. */
    private static boolean isSupported(TransitNetwork network, RerouteInputData inputData) {
        String line = inputData.getLine();
        String stationId = inputData.getStationId();
        String otherStationId = inputData.getOtherStationId();
        if (line == null || stationId == null || otherStationId == null) {
            return false;
        }
        return connects(network, line, stationId, otherStationId)
                || connects(network, line, otherStationId, stationId);
    }

    private static boolean connects(TransitNetwork network, String line, String fromId, String toId) {
        for (Connection connection : network.connectionsFrom(fromId)) {
            if (connection.getLine().equals(line) && connection.getToId().equals(toId)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the active disruption on the chosen connection, or null when it is not disrupted. */
    private Disruption findActive(RerouteInputData inputData) {
        for (Disruption disruption : disruptionAccess.getActiveDisruptions()) {
            if (disruption.isBetween(inputData.getLine(), inputData.getStationId(),
                    inputData.getOtherStationId())) {
                return disruption;
            }
        }
        return null;
    }

    private RerouteOutputData toOutput(TransitNetwork network, Disruption changed, Outcome outcome,
                                       Optional<Route> route) {
        List<String> stationNames = new ArrayList<>();
        if (route.isPresent()) {
            for (Station station : route.get().getStations()) {
                stationNames.add(station.getName());
            }
        }
        return new RerouteOutputData(DisruptedConnection.of(network, changed),
                DisruptedConnection.listOf(network, disruptionAccess.getActiveDisruptions()), outcome, stationNames,
                route.map(Route::getTotalMinutes).orElse(0), route.map(Route::getTransfers).orElse(0));
    }

    /** Names the connection a disruption is on, for example "Line 1 between Bloor-Yonge and Wellesley". */
    private static String where(TransitNetwork network, Disruption disruption) {
        DisruptedConnection connection = DisruptedConnection.of(network, disruption);
        return connection.getLine() + " between " + connection.getStationName() + " and "
                + connection.getOtherStationName();
    }
}
