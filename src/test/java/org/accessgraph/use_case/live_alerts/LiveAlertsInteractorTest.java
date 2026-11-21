package org.accessgraph.use_case.live_alerts;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.entity.Connection;
import org.accessgraph.entity.Disruption;
import org.accessgraph.entity.ServiceAlert;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.TransitNetwork;
import org.accessgraph.use_case.plan_route.DijkstraRouteFinder;
import org.accessgraph.use_case.reroute.DisruptionDataAccessInterface;
import org.accessgraph.use_case.reroute.RerouteOutputData.Outcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interactor is tested with fake data access and a fake presenter: no Swing, no network.
 *
 * <pre>
 * Test network:  A --L1-- B --L1-- C     fastest way from A to C (4 minutes)
 *                A --L2-- D --L2-- C     slower way around (10 minutes)
 *                C --L3-- E              the only way to E
 * </pre>
 */
class LiveAlertsInteractorTest {
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
    private static final String NO_SERVICE_B_TO_C = "Line L1: no service between B and C.";

    private final FakeAlerts alerts = new FakeAlerts();
    private final FakeDisruptions disruptions = new FakeDisruptions();
    private final FakePresenter presenter = new FakePresenter();
    private final TransitNetwork network = network();
    private final LiveAlertsInteractor interactor = new LiveAlertsInteractor(() -> network,
            () -> new AccessibilityProfile(true, 2), alerts, disruptions, new DijkstraRouteFinder(), presenter);

    private static final class FakeAlerts implements ServiceAlertDataAccessInterface {
        private final List<ServiceAlert> published = new ArrayList<>();
        private IOException failure;

        @Override
        public List<ServiceAlert> getNoServiceAlerts() throws IOException {
            if (failure != null) {
                throw failure;
            }
            return List.copyOf(published);
        }
    }

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

    private static final class FakePresenter implements LiveAlertsOutputBoundary {
        private LiveAlertsOutputData loaded;
        private LiveAlertsOutputData cleared;
        private String error;

        @Override
        public void prepareLoadedView(LiveAlertsOutputData outputData) {
            loaded = outputData;
        }

        @Override
        public void prepareClearedView(LiveAlertsOutputData outputData) {
            cleared = outputData;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }

    private static TransitNetwork network() {
        List<Station> stations = new ArrayList<>();
        for (String id : List.of("A", "B", "C", "D", "E")) {
            stations.add(new Station(id, "Station " + id, 43.6, -79.4, Station.Accessibility.CONFIRMED,
                    URI.create("https://www.ttc.ca/subway-stations/x"), LocalDate.of(2026, 11, 9)));
        }
        List<Connection> connections = new ArrayList<>();
        String[][] rows = {{"A", "B", "L1", "2"}, {"B", "C", "L1", "2"}, {"A", "D", "L2", "5"},
                {"D", "C", "L2", "5"}, {"C", "E", "L3", "2"}};
        for (String[] row : rows) {
            connections.add(new Connection(row[0], row[1], row[2], Integer.parseInt(row[3])));
            connections.add(new Connection(row[1], row[0], row[2], Integer.parseInt(row[3])));
        }
        return new TransitNetwork(stations, connections);
    }

    /** An alert that is in effect at {@link #NOW}. */
    private static ServiceAlert alert(String line, List<String> stations, String description) {
        return new ServiceAlert(line, stations, description, NOW.minusSeconds(60), NOW.plusSeconds(3600));
    }

    private static LiveAlertsInputData journey(String origin, String destination) {
        return new LiveAlertsInputData(origin, destination, NOW);
    }

    @Test
    void alertInEffectClosesItsStretchAndTheJourneyIsRerouted() {
        alerts.published.add(alert("L1", List.of("A", "B", "C"), "Line L1: no service between A and C."));

        interactor.load(journey("A", "C"));

        assertNull(presenter.error);
        assertEquals(Outcome.REROUTED, presenter.loaded.getOutcome());
        assertEquals(List.of("Station A", "Station D", "Station C"), presenter.loaded.getStationNames());
        assertEquals(10, presenter.loaded.getTotalMinutes());
        assertEquals(List.of("Line L1: no service between A and C."), presenter.loaded.getAppliedAlerts());
        assertTrue(presenter.loaded.getUnmatchedAlerts().isEmpty());
        assertEquals(NOW, presenter.loaded.getCheckedAt());
        assertEquals(2, presenter.loaded.getActiveDisruptions().size(), "A to B and B to C, once each.");
        assertTrue(presenter.loaded.getActiveDisruptions().stream().allMatch(closed -> closed.isLive()));
        assertEquals(2, disruptions.active.size());
        assertTrue(disruptions.active.stream().allMatch(Disruption::isLive));
    }

    @Test
    void advanceNoticeOfALaterClosureIsNotApplied() {
        alerts.published.add(new ServiceAlert("L1", List.of("B", "C"), NO_SERVICE_B_TO_C,
                NOW.plusSeconds(3600), NOW.plusSeconds(7200)));
        alerts.published.add(new ServiceAlert("L1", List.of("A", "B"), "Ended already.",
                NOW.minusSeconds(7200), NOW.minusSeconds(3600)));

        interactor.load(journey("A", "C"));

        assertEquals(Outcome.UNCHANGED, presenter.loaded.getOutcome());
        assertEquals(List.of("Station A", "Station B", "Station C"), presenter.loaded.getStationNames());
        assertTrue(presenter.loaded.getAppliedAlerts().isEmpty());
        assertTrue(presenter.loaded.getUnmatchedAlerts().isEmpty());
        assertTrue(disruptions.active.isEmpty());
    }

    @Test
    void alertThatMatchesNoSupportedConnectionIsReportedNotGuessed() {
        alerts.published.add(alert("L9", List.of("A", "B"), "Line L9: no service."));
        alerts.published.add(alert("L1", List.of("B"), "Trains are not stopping at B."));
        alerts.published.add(alert("", List.of("B", "C"), "No line named."));

        interactor.load(journey("A", "C"));

        assertEquals(List.of("Line L9: no service.", "Trains are not stopping at B.", "No line named."),
                presenter.loaded.getUnmatchedAlerts());
        assertTrue(presenter.loaded.getAppliedAlerts().isEmpty());
        assertEquals(Outcome.UNCHANGED, presenter.loaded.getOutcome());
        assertTrue(disruptions.active.isEmpty());
    }

    @Test
    void reportsWhenALiveClosureLeavesNoAlternative() {
        alerts.published.add(alert("L3", List.of("C", "E"), "Line L3: no service."));

        interactor.load(journey("A", "E"));

        assertEquals(Outcome.NO_ALTERNATIVE, presenter.loaded.getOutcome());
        assertTrue(presenter.loaded.getStationNames().isEmpty());
    }

    @Test
    void closuresApplyEvenBeforeAJourneyIsSearched() {
        alerts.published.add(alert("L1", List.of("B", "C"), NO_SERVICE_B_TO_C));

        interactor.load(journey(null, null));

        assertEquals(Outcome.NO_JOURNEY, presenter.loaded.getOutcome());
        assertEquals(1, disruptions.active.size());
    }

    @Test
    void failedLoadLeavesTheConditionsUnchanged() {
        Disruption simulated = new Disruption(Disruption.Type.CLOSURE, "L2", "A", "D");
        Disruption live = new Disruption(Disruption.Type.OUTAGE, Disruption.Source.LIVE, "L1", "B", "C");
        disruptions.active.addAll(List.of(simulated, live));
        alerts.failure = new IOException("The TTC alert feed timed out.");

        interactor.load(journey("A", "C"));

        assertEquals("Live TTC alerts were not loaded, so the conditions are unchanged. "
                + "The TTC alert feed timed out.", presenter.error);
        assertNull(presenter.loaded);
        assertEquals(List.of(simulated, live), disruptions.active);
    }

    @Test
    void laterLoadReplacesTheClosuresOfAnEarlierOne() {
        alerts.published.add(alert("L1", List.of("B", "C"), NO_SERVICE_B_TO_C));
        interactor.load(journey("A", "C"));
        assertEquals(Outcome.REROUTED, presenter.loaded.getOutcome());

        alerts.published.clear();
        alerts.published.add(alert("L3", List.of("C", "E"), "Line L3: no service."));
        interactor.load(journey("A", "C"));

        assertEquals(1, disruptions.active.size());
        assertEquals("L3", disruptions.active.get(0).getLine());
        assertEquals(Outcome.UNCHANGED, presenter.loaded.getOutcome());
        assertEquals(List.of("Station A", "Station B", "Station C"), presenter.loaded.getStationNames(),
                "The lifted closure no longer forces the detour.");
    }

    @Test
    void liveClosureTakesThePlaceOfASimulatedDisruptionOnTheSameConnection() {
        disruptions.active.add(new Disruption(Disruption.Type.CLOSURE, "L1", "C", "B"));
        alerts.published.add(alert("L1", List.of("B", "C"), NO_SERVICE_B_TO_C));

        interactor.load(journey("A", "C"));

        assertEquals(1, disruptions.active.size());
        assertTrue(disruptions.active.get(0).isLive());
        assertEquals(Outcome.UNCHANGED, presenter.loaded.getOutcome(), "The route already avoided B to C.");
    }

    @Test
    void overlappingAlertsCloseAConnectionOnceAndAreBothListed() {
        alerts.published.add(alert("L1", List.of("A", "B", "C"), "First notice."));
        alerts.published.add(alert("L1", List.of("B", "C"), "Second notice."));
        alerts.published.add(alert("L9", List.of(), "First notice."));

        interactor.load(journey("A", "C"));

        assertEquals(2, disruptions.active.size());
        assertEquals(List.of("First notice.", "Second notice."), presenter.loaded.getAppliedAlerts());
        assertTrue(presenter.loaded.getUnmatchedAlerts().isEmpty(),
                "A notice that closed a connection on one line is not also listed as unmatched.");
    }

    @Test
    void clearingRemovesOnlyLiveClosuresAndRecalculates() {
        Disruption simulated = new Disruption(Disruption.Type.CLOSURE, "L2", "A", "D");
        disruptions.active.add(simulated);
        alerts.published.add(alert("L1", List.of("B", "C"), NO_SERVICE_B_TO_C));
        interactor.load(journey("A", "C"));
        assertEquals(Outcome.NO_ALTERNATIVE, presenter.loaded.getOutcome());

        interactor.clear(journey("A", "C"));

        assertEquals(List.of(simulated), disruptions.active);
        assertEquals(Outcome.REROUTED, presenter.cleared.getOutcome());
        assertEquals(List.of("Station A", "Station B", "Station C"), presenter.cleared.getStationNames());
        assertEquals(1, presenter.cleared.getActiveDisruptions().size());
        assertFalse(presenter.cleared.getActiveDisruptions().get(0).isLive());
        assertTrue(presenter.cleared.getAppliedAlerts().isEmpty());
    }

    @Test
    void clearingClosuresTheRouteNeverUsedLeavesItUnchanged() {
        alerts.published.add(alert("L3", List.of("C", "E"), "Line L3: no service."));
        interactor.load(journey("A", "C"));

        interactor.clear(journey("A", "C"));
        assertEquals(Outcome.UNCHANGED, presenter.cleared.getOutcome());
        assertEquals(4, presenter.cleared.getTotalMinutes());

        interactor.clear(journey(null, null));
        assertEquals(Outcome.NO_JOURNEY, presenter.cleared.getOutcome());
    }
}
