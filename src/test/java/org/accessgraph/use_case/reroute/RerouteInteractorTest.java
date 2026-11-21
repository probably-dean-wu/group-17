package org.accessgraph.use_case.reroute;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.entity.Connection;
import org.accessgraph.entity.Disruption;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.TransitNetwork;
import org.accessgraph.use_case.plan_route.DijkstraRouteFinder;
import org.accessgraph.use_case.reroute.RerouteOutputData.Outcome;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interactor is tested with fake data access and a fake presenter: no Swing, no files.
 *
 * <pre>
 * Test network:  A --L1-- B --L1-- C     fastest way from A to C (4 minutes)
 *                A --L2-- D --L2-- C     slower way around (10 minutes)
 *                C --L3-- E --L3-- X     the only way to E and X; X is a limited station
 * </pre>
 */
class RerouteInteractorTest {
    private static final AccessibilityProfile STEP_FREE = new AccessibilityProfile(true, 2);

    private final FakeDisruptions disruptions = new FakeDisruptions();
    private final FakePresenter presenter = new FakePresenter();
    private final TransitNetwork network = network();
    private final RerouteInteractor interactor = new RerouteInteractor(() -> network, () -> STEP_FREE,
            disruptions, new DijkstraRouteFinder(), presenter);

    private static final class FakeDisruptions implements DisruptionDataAccessInterface {
        private final List<Disruption> active = new ArrayList<>();

        @Override
        public List<Disruption> getActiveDisruptions() {
            return List.copyOf(active);
        }

        @Override
        public void add(Disruption disruption) {
            active.add(disruption);
        }

        @Override
        public void remove(Disruption disruption) {
            active.remove(disruption);
        }
    }

    private static final class FakePresenter implements RerouteOutputBoundary {
        private RerouteOutputData disrupted;
        private RerouteOutputData restored;
        private String error;

        @Override
        public void prepareDisruptedView(RerouteOutputData outputData) {
            disrupted = outputData;
        }

        @Override
        public void prepareRestoredView(RerouteOutputData outputData) {
            restored = outputData;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }

    private static Station station(String id, Station.Accessibility accessibility) {
        return new Station(id, "Station " + id, 43.6, -79.4, accessibility,
                URI.create("https://www.ttc.ca/subway-stations/x"), LocalDate.of(2026, 11, 9));
    }

    private static void both(List<Connection> list, String a, String b, String line, int minutes) {
        list.add(new Connection(a, b, line, minutes));
        list.add(new Connection(b, a, line, minutes));
    }

    private static TransitNetwork network() {
        List<Station> stations = new ArrayList<>();
        for (String id : List.of("A", "B", "C", "D", "E")) {
            stations.add(station(id, Station.Accessibility.CONFIRMED));
        }
        stations.add(station("X", Station.Accessibility.LIMITED));
        List<Connection> connections = new ArrayList<>();
        both(connections, "A", "B", "L1", 2);
        both(connections, "B", "C", "L1", 2);
        both(connections, "A", "D", "L2", 5);
        both(connections, "D", "C", "L2", 5);
        both(connections, "C", "E", "L3", 2);
        both(connections, "E", "X", "L3", 2);
        return new TransitNetwork(stations, connections);
    }

    private static RerouteInputData closure(String line, String a, String b, String origin, String destination) {
        return new RerouteInputData(line, a, b, Disruption.Type.CLOSURE, origin, destination);
    }

    @Test
    void disruptingAConnectionOnTheRouteGivesAReplacement() {
        interactor.simulate(closure("L1", "B", "C", "A", "C"));

        assertNull(presenter.error);
        assertEquals(Outcome.REROUTED, presenter.disrupted.getOutcome());
        assertEquals(List.of("Station A", "Station D", "Station C"), presenter.disrupted.getStationNames());
        assertEquals(10, presenter.disrupted.getTotalMinutes());
        assertEquals(0, presenter.disrupted.getTransfers());
        assertEquals("Planned closure", presenter.disrupted.getChanged().getTypeLabel());
        assertEquals("L1", presenter.disrupted.getChanged().getLine());
        assertEquals("Station B", presenter.disrupted.getChanged().getStationName());
        assertEquals("Station C", presenter.disrupted.getChanged().getOtherStationName());
        assertEquals(1, presenter.disrupted.getActiveDisruptions().size());
        assertEquals(1, disruptions.active.size());
    }

    @Test
    void disruptionAppliesInBothDirectionsOfTravel() {
        interactor.simulate(closure("L1", "B", "C", "C", "A"));

        assertEquals(Outcome.REROUTED, presenter.disrupted.getOutcome());
        assertEquals(List.of("Station C", "Station D", "Station A"), presenter.disrupted.getStationNames());
    }

    @Test
    void reportsWhenNoAlternativeExists() {
        interactor.simulate(closure("L3", "C", "E", "A", "E"));

        assertEquals(Outcome.NO_ALTERNATIVE, presenter.disrupted.getOutcome());
        assertTrue(presenter.disrupted.getStationNames().isEmpty());
        assertEquals(1, presenter.disrupted.getActiveDisruptions().size());
    }

    @Test
    void routeThatAvoidsTheConnectionIsUnchanged() {
        interactor.simulate(closure("L3", "C", "E", "A", "C"));

        assertEquals(Outcome.UNCHANGED, presenter.disrupted.getOutcome());
        assertEquals(List.of("Station A", "Station B", "Station C"), presenter.disrupted.getStationNames());
        assertEquals(4, presenter.disrupted.getTotalMinutes());
    }

    @Test
    void disruptionIsAppliedEvenBeforeAJourneyIsSearched() {
        interactor.simulate(closure("L1", "A", "B", null, null));

        assertEquals(Outcome.NO_JOURNEY, presenter.disrupted.getOutcome());
        assertTrue(presenter.disrupted.getStationNames().isEmpty());
        assertEquals(1, disruptions.active.size());
    }

    @Test
    void journeyWithoutAnyFeasibleRouteStaysWithoutOne() {
        interactor.simulate(closure("L1", "A", "B", "A", "X"));

        assertEquals(Outcome.NO_ROUTE, presenter.disrupted.getOutcome());
        assertTrue(presenter.disrupted.getStationNames().isEmpty());
    }

    @Test
    void unsupportedConnectionIsRejected() {
        interactor.simulate(closure("L1", "A", "C", "A", "C"));
        assertTrue(presenter.error.contains("not part of the supported network"));

        interactor.simulate(closure("L9", "A", "B", "A", "C"));
        interactor.simulate(closure(null, null, null, "A", "C"));
        assertNull(presenter.disrupted);
        assertTrue(disruptions.active.isEmpty());
    }

    @Test
    void disruptionNeedsAType() {
        interactor.simulate(new RerouteInputData("L1", "A", "B", null, "A", "C"));

        assertTrue(presenter.error.contains("type of disruption"));
        assertTrue(disruptions.active.isEmpty());
    }

    @Test
    void sameConnectionCannotBeDisruptedTwice() {
        interactor.simulate(closure("L1", "A", "B", "A", "C"));
        interactor.simulate(new RerouteInputData("L1", "B", "A", Disruption.Type.OUTAGE, "A", "C"));

        assertEquals("L1 between Station A and Station B is already disrupted.", presenter.error);
        assertEquals(1, disruptions.active.size());
    }

    @Test
    void restoringTheConnectionBringsTheRouteBack() {
        interactor.simulate(closure("L1", "B", "C", "A", "C"));
        interactor.restore(new RerouteInputData("L1", "C", "B", "A", "C"));

        assertNull(presenter.error);
        assertEquals(Outcome.REROUTED, presenter.restored.getOutcome());
        assertEquals(List.of("Station A", "Station B", "Station C"), presenter.restored.getStationNames());
        assertEquals(4, presenter.restored.getTotalMinutes());
        assertEquals("Station B", presenter.restored.getChanged().getStationName());
        assertTrue(presenter.restored.getActiveDisruptions().isEmpty());
        assertTrue(disruptions.active.isEmpty());
    }

    @Test
    void restoringMakesAnUnreachableDestinationReachableAgain() {
        interactor.simulate(closure("L3", "C", "E", "A", "E"));
        interactor.restore(new RerouteInputData("L3", "C", "E", "A", "E"));

        assertEquals(Outcome.REROUTED, presenter.restored.getOutcome());
        assertEquals(List.of("Station A", "Station B", "Station C", "Station E"),
                presenter.restored.getStationNames());
        assertEquals(1, presenter.restored.getTransfers());
    }

    @Test
    void restoringAConnectionTheRouteDoesNotUseLeavesItUnchanged() {
        interactor.simulate(closure("L3", "C", "E", "A", "C"));
        interactor.restore(new RerouteInputData("L3", "C", "E", "A", "C"));

        assertEquals(Outcome.UNCHANGED, presenter.restored.getOutcome());
        assertEquals(List.of("Station A", "Station B", "Station C"), presenter.restored.getStationNames());
    }

    @Test
    void otherDisruptionsStayActiveAfterARestore() {
        interactor.simulate(closure("L1", "B", "C", "A", "C"));
        interactor.simulate(closure("L2", "A", "D", "A", "C"));
        assertEquals(Outcome.NO_ALTERNATIVE, presenter.disrupted.getOutcome());

        interactor.restore(new RerouteInputData("L2", "A", "D", "A", "C"));

        assertEquals(Outcome.REROUTED, presenter.restored.getOutcome());
        assertEquals(List.of("Station A", "Station D", "Station C"), presenter.restored.getStationNames());
        assertEquals(1, presenter.restored.getActiveDisruptions().size());
        assertEquals("L1", presenter.restored.getActiveDisruptions().get(0).getLine());
    }

    @Test
    void liveClosureCannotBeRestoredOrSimulatedOver() {
        disruptions.add(new Disruption(Disruption.Type.OUTAGE, Disruption.Source.LIVE, "L1", "B", "C"));

        interactor.restore(new RerouteInputData("L1", "B", "C", "A", "C"));
        assertEquals("L1 between Station B and Station C is closed by a live TTC alert, so it cannot be restored "
                + "here. Clear the live alerts to plan without them.", presenter.error);
        assertNull(presenter.restored);
        assertEquals(1, disruptions.active.size());

        interactor.simulate(closure("L1", "B", "C", "A", "C"));
        assertEquals("L1 between Station B and Station C is already disrupted.", presenter.error);
        assertEquals(1, disruptions.active.size());
    }

    @Test
    void outputSaysWhichActiveDisruptionsAreLive() {
        disruptions.add(new Disruption(Disruption.Type.OUTAGE, Disruption.Source.LIVE, "L3", "C", "E"));

        interactor.simulate(closure("L1", "B", "C", "A", "C"));

        assertTrue(presenter.disrupted.getActiveDisruptions().get(0).isLive());
        assertFalse(presenter.disrupted.getActiveDisruptions().get(1).isLive());
        assertFalse(presenter.disrupted.getChanged().isLive());
    }

    @Test
    void connectionThatIsNotDisruptedCannotBeRestored() {
        interactor.restore(new RerouteInputData("L1", "A", "B", "A", "C"));

        assertEquals("That connection is not disrupted.", presenter.error);
        assertNull(presenter.restored);
    }
}
