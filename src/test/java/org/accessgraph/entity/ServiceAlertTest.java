package org.accessgraph.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A notice for the stretch B, C, D on line L1 of the network A - B - C - D - E. */
class ServiceAlertTest {
    private static final Instant START = Instant.parse("2026-11-21T10:00:00Z");
    private static final Instant END = Instant.parse("2026-11-22T08:30:00Z");
    private static final ServiceAlert ALERT =
            new ServiceAlert("L1", List.of("B", "C", "D", "C"), "No service between B and D.", START, END);

    @Test
    void closesConnectionsInsideTheStretchInBothDirections() {
        assertTrue(ALERT.closes(new Connection("B", "C", "L1", 2)));
        assertTrue(ALERT.closes(new Connection("D", "C", "L1", 2)));
    }

    @Test
    void leavesConnectionsAtTheEndsAndOnOtherLinesRunning() {
        assertFalse(ALERT.closes(new Connection("A", "B", "L1", 2)));
        assertFalse(ALERT.closes(new Connection("D", "E", "L1", 2)));
        assertFalse(ALERT.closes(new Connection("B", "C", "L2", 2)));
    }

    @Test
    void appliesFromItsStartUntilItsEnd() {
        assertFalse(ALERT.isActiveAt(START.minusSeconds(1)), "An advance notice does not apply yet.");
        assertTrue(ALERT.isActiveAt(START));
        assertTrue(ALERT.isActiveAt(END.minusSeconds(1)));
        assertFalse(ALERT.isActiveAt(END));
    }

    @Test
    void missingTimesLeaveThePeriodOpen() {
        ServiceAlert noStart = new ServiceAlert("L1", List.of("B", "C"), "x", null, END);
        ServiceAlert noEnd = new ServiceAlert("L1", List.of("B", "C"), "x", START, null);
        assertTrue(noStart.isActiveAt(START.minusSeconds(3600)));
        assertFalse(noStart.isActiveAt(END));
        assertTrue(noEnd.isActiveAt(END.plusSeconds(3600)));
        assertFalse(noEnd.isActiveAt(START.minusSeconds(1)));
    }

    @Test
    void keepsEachStationOnceInTheOrderGiven() {
        assertEquals(List.of("B", "C", "D"), List.copyOf(ALERT.getStationIds()));
        assertEquals("L1", ALERT.getLine());
        assertEquals("No service between B and D.", ALERT.getDescription());
    }
}
