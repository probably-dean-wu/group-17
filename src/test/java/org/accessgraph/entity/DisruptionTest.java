package org.accessgraph.entity;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test network: A --L1-- B --L1-- C, plus a second line A --L2-- B over the same pair of stations. */
class DisruptionTest {
    private static final Disruption CLOSURE = new Disruption(Disruption.Type.CLOSURE, "L1", "A", "B");

    private static Station station(String id) {
        return new Station(id, id, 43.6, -79.4, Station.Accessibility.CONFIRMED,
                URI.create("https://www.ttc.ca/subway-stations/x"), LocalDate.of(2026, 11, 9));
    }

    private static TransitNetwork network() {
        return new TransitNetwork(List.of(station("A"), station("B"), station("C")), List.of(
                new Connection("A", "B", "L1", 2), new Connection("B", "A", "L1", 2),
                new Connection("B", "C", "L1", 2), new Connection("C", "B", "L1", 2),
                new Connection("A", "B", "L2", 6), new Connection("B", "A", "L2", 6)));
    }

    @Test
    void affectsItsConnectionInBothDirections() {
        assertTrue(CLOSURE.affects(new Connection("A", "B", "L1", 2)));
        assertTrue(CLOSURE.affects(new Connection("B", "A", "L1", 2)));
    }

    @Test
    void leavesOtherLinesAndNeighbouringConnectionsAlone() {
        assertFalse(CLOSURE.affects(new Connection("A", "B", "L2", 6)));
        assertFalse(CLOSURE.affects(new Connection("B", "C", "L1", 2)));
    }

    @Test
    void affectsOnlyRoutesThatTravelOverTheConnection() {
        Route overConnection = new Route(List.of(station("C"), station("B"), station("A")), List.of("L1", "L1"), 4, 0);
        Route onOtherLine = new Route(List.of(station("A"), station("B")), List.of("L2"), 6, 0);
        assertTrue(CLOSURE.affects(overConnection));
        assertFalse(CLOSURE.affects(onOtherLine));
    }

    @Test
    void isSimulatedUnlessItComesFromALiveAlert() {
        Disruption live = new Disruption(Disruption.Type.OUTAGE, Disruption.Source.LIVE, "L1", "B", "A");
        assertFalse(CLOSURE.isLive());
        assertEquals(Disruption.Source.SIMULATED, CLOSURE.getSource());
        assertTrue(live.isLive());
        assertTrue(live.sharesConnectionWith(CLOSURE), "Same line and stations, whatever the type or source.");
        assertFalse(live.sharesConnectionWith(new Disruption(Disruption.Type.OUTAGE, "L2", "A", "B")));
    }

    @Test
    void needsTwoDifferentStations() {
        assertThrows(IllegalArgumentException.class,
                () -> new Disruption(Disruption.Type.OUTAGE, "L1", "A", "A"));
    }

    @Test
    void networkWithoutTheDisruptionLosesOnlyThatConnection() {
        TransitNetwork network = network();
        TransitNetwork disrupted = network.without(List.of(CLOSURE));

        assertEquals(List.of("L2"), lines(disrupted, "A"));
        assertEquals(List.of("L1", "L2"), lines(disrupted, "B"));
        assertEquals(List.of("L1"), lines(disrupted, "C"));
        assertTrue(disrupted.hasStation("A"));
        assertEquals(List.of("L1", "L2"), lines(network, "A"), "The original network is not changed.");
    }

    @Test
    void networkWithoutAnyDisruptionIsTheSame() {
        TransitNetwork unchanged = network().without(List.of());
        assertEquals(List.of("L1", "L2"), lines(unchanged, "A"));
        assertEquals(List.of("L1", "L1", "L2"), lines(unchanged, "B"));
    }

    private static List<String> lines(TransitNetwork network, String stationId) {
        return network.connectionsFrom(stationId).stream().map(Connection::getLine).sorted()
                .collect(java.util.stream.Collectors.toList());
    }
}
