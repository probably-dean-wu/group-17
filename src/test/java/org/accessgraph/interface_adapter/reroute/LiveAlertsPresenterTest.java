package org.accessgraph.interface_adapter.reroute;

import org.accessgraph.use_case.live_alerts.LiveAlertsOutputData;
import org.accessgraph.use_case.reroute.DisruptedConnection;
import org.accessgraph.use_case.reroute.RerouteOutputData.Outcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveAlertsPresenterTest {
    private static final Instant CHECKED = Instant.parse("2026-11-21T12:05:00Z");
    private static final String ALERT = "Line 2 Bloor-Danforth: There is no subway service between St George and "
            + "Broadview stations.";
    private static final DisruptedConnection LIVE =
            new DisruptedConnection("Service outage", "Line 2", "1", "Bay", "2", "Bloor-Yonge", true);
    private static final DisruptedConnection SIMULATED =
            new DisruptedConnection("Planned closure", "Line 1", "3", "King", "4", "Union", false);

    private final RerouteViewModel viewModel = new RerouteViewModel();
    private final LiveAlertsPresenter presenter = new LiveAlertsPresenter(viewModel, ZoneOffset.ofHours(-5));

    private static LiveAlertsOutputData output(List<String> applied, List<String> unmatched,
                                               List<DisruptedConnection> active, Outcome outcome,
                                               List<String> stations) {
        return new LiveAlertsOutputData(CHECKED, applied, unmatched, active, outcome, stations, 46, 2);
    }

    @Test
    void loadedAlertExplainsWhyTheRouteChangedAndLabelsTheConditions() {
        presenter.prepareLoadedView(output(List.of(ALERT), List.of(), List.of(SIMULATED, LIVE, LIVE),
                Outcome.REROUTED, List.of("Finch", "Union")));

        RerouteState state = viewModel.getState();
        assertEquals("Live TTC alerts loaded. 2 connections have no service.", state.getHeadline());
        assertEquals(List.of(ALERT, "",
                "Your previous route is no longer available because it passes through a stretch with no service.",
                "", "Replacement route: about 46 min · 2 transfers", "Finch → Union"), state.getDetails());
        assertEquals("Live TTC alerts as of 07:05.", state.getLiveStatus(), "Shown in the presenter's time zone.");
        assertEquals("", state.getError());
        assertEquals(List.of("Line 1: King ↔ Union (simulated)", "Line 2: Bay ↔ Bloor-Yonge (live)",
                        "Line 2: Bay ↔ Bloor-Yonge (live)"),
                List.of(state.getActiveDisruptions().get(0).toString(), state.getActiveDisruptions().get(1).toString(),
                        state.getActiveDisruptions().get(2).toString()));
    }

    @Test
    void loadWithoutClosuresSaysSoAndShowsTheCurrentRoute() {
        presenter.prepareLoadedView(output(List.of(), List.of(), List.of(SIMULATED), Outcome.UNCHANGED,
                List.of("Finch", "Union")));

        RerouteState state = viewModel.getState();
        assertEquals("Live TTC alerts loaded. No supported connection is closed right now.", state.getHeadline());
        assertEquals(List.of("Current route: about 46 min · 2 transfers", "Finch → Union"), state.getDetails());
        assertEquals("Live TTC alerts as of 07:05.", state.getLiveStatus());
    }

    @Test
    void singleClosureAndUnaffectedRouteAreWordedPlainly() {
        presenter.prepareLoadedView(output(List.of(ALERT), List.of(), List.of(LIVE), Outcome.UNCHANGED,
                List.of("Finch", "Union")));

        RerouteState state = viewModel.getState();
        assertEquals("Live TTC alerts loaded. 1 connection has no service.", state.getHeadline());
        assertEquals("Your route avoids every closed connection.", state.getDetails().get(2));
    }

    @Test
    void unmatchedAlertsAreShownAsNotApplied() {
        presenter.prepareLoadedView(output(List.of(), List.of("Line 5: no service."), List.of(),
                Outcome.NO_JOURNEY, List.of()));

        assertEquals(List.of("Not applied, because it matches no supported connection:", "Line 5: no service."),
                viewModel.getState().getDetails());
    }

    @Test
    void missingAlternativeIsReported() {
        presenter.prepareLoadedView(output(List.of(ALERT), List.of(), List.of(LIVE), Outcome.NO_ALTERNATIVE,
                List.of()));

        List<String> details = viewModel.getState().getDetails();
        assertTrue(details.get(2).startsWith("Your previous route is no longer available"));
        assertTrue(details.get(4).startsWith("No alternative route"));
    }

    @Test
    void clearingReturnsToSimulatedConditions() {
        presenter.prepareLoadedView(output(List.of(ALERT), List.of(), List.of(LIVE), Outcome.NO_JOURNEY, List.of()));
        presenter.prepareClearedView(output(List.of(), List.of(), List.of(SIMULATED), Outcome.REROUTED,
                List.of("Finch", "Union")));

        RerouteState state = viewModel.getState();
        assertEquals("Live TTC alerts cleared. Only simulated disruptions apply now.", state.getHeadline());
        assertEquals(List.of("Your journey can use the reopened connections again.", "",
                "Current route: about 46 min · 2 transfers", "Finch → Union"), state.getDetails());
        assertEquals("", state.getLiveStatus());
        assertEquals(1, state.getActiveDisruptions().size());
    }

    @Test
    void failureKeepsTheConditionsThatWereAlreadyShown() {
        presenter.prepareLoadedView(output(List.of(ALERT), List.of(), List.of(LIVE), Outcome.NO_JOURNEY, List.of()));
        presenter.prepareFailView("Live TTC alerts were not loaded.");

        RerouteState state = viewModel.getState();
        assertEquals("Live TTC alerts were not loaded.", state.getError());
        assertEquals("Live TTC alerts as of 07:05.", state.getLiveStatus());
        assertEquals(1, state.getActiveDisruptions().size());
    }
}
