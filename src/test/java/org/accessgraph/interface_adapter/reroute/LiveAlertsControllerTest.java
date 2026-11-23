package org.accessgraph.interface_adapter.reroute;

import org.accessgraph.use_case.live_alerts.LiveAlertsInputBoundary;
import org.accessgraph.use_case.live_alerts.LiveAlertsInputData;
import org.accessgraph.use_case.plan_route.PlanRouteInputData;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LiveAlertsControllerTest {
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    /** Everything that happened, in order: route searches and live-alert requests. */
    private final List<String> calls = new ArrayList<>();
    private final JourneyRecorder journeys = new JourneyRecorder(inputData -> calls.add("search"));
    private LiveAlertsInputData received;
    private final LiveAlertsController controller = new LiveAlertsController(new LiveAlertsInputBoundary() {
        @Override
        public void load(LiveAlertsInputData inputData) {
            calls.add("load");
            received = inputData;
        }

        @Override
        public void clear(LiveAlertsInputData inputData) {
            calls.add("clear");
            received = inputData;
        }
    }, journeys, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void loadSendsTheLastSearchedJourneyAndTheCurrentTimeThenRefreshesThePlanner() {
        journeys.execute(new PlanRouteInputData("origin", "destination"));

        controller.load();

        assertEquals("origin", received.getOriginId());
        assertEquals("destination", received.getDestinationId());
        assertEquals(NOW, received.getNow());
        assertEquals(List.of("search", "load", "search"), calls);
    }

    @Test
    void clearRefreshesThePlannerToo() {
        journeys.execute(new PlanRouteInputData("origin", "destination"));

        controller.clear();

        assertEquals("destination", received.getDestinationId());
        assertEquals(List.of("search", "clear", "search"), calls);
    }

    @Test
    void worksBeforeAnyJourneyIsSearched() {
        controller.load();

        assertNull(received.getOriginId());
        assertNull(received.getDestinationId());
        assertEquals(List.of("load"), calls);
    }
}
