package org.accessgraph.interface_adapter.reroute;

import org.accessgraph.use_case.reroute.DisruptedConnection;
import org.accessgraph.use_case.reroute.RerouteOutputData;
import org.accessgraph.use_case.reroute.RerouteOutputData.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReroutePresenterTest {
    private static final DisruptedConnection CLOSURE =
            new DisruptedConnection("Planned closure", "Line 1", "1", "Bloor-Yonge", "2", "Wellesley", false);

    private final RerouteViewModel viewModel = new RerouteViewModel();
    private final ReroutePresenter presenter = new ReroutePresenter(viewModel);
    private int notifications;

    ReroutePresenterTest() {
        viewModel.addPropertyChangeListener(event -> notifications++);
    }

    private static RerouteOutputData output(Outcome outcome, List<DisruptedConnection> active, List<String> stations,
                                            int minutes, int transfers) {
        return new RerouteOutputData(CLOSURE, active, outcome, stations, minutes, transfers);
    }

    @Test
    void replacementExplainsWhyThePreviousRouteIsInvalid() {
        presenter.prepareDisruptedView(output(Outcome.REROUTED, List.of(CLOSURE),
                List.of("Finch", "Bloor-Yonge", "St George", "Union"), 49, 2));

        RerouteState state = viewModel.getState();
        assertEquals("Simulated planned closure: Line 1 between Bloor-Yonge and Wellesley.", state.getHeadline());
        assertEquals(List.of(
                "Your previous route is no longer available because it used this connection.",
                "",
                "Replacement route: about 49 min · 2 transfers",
                "Finch → Bloor-Yonge → St George → Union"), state.getDetails());
        assertEquals("", state.getError());
        assertEquals(1, state.getActiveDisruptions().size());
        RerouteState.ActiveDisruption active = state.getActiveDisruptions().get(0);
        assertEquals("Line 1: Bloor-Yonge ↔ Wellesley (simulated)", active.toString());
        assertEquals("Line 1", active.getLine());
        assertEquals("1", active.getStationId());
        assertEquals("2", active.getOtherStationId());
        assertEquals(1, notifications);
    }

    @Test
    void missingAlternativeIsReported() {
        presenter.prepareDisruptedView(output(Outcome.NO_ALTERNATIVE, List.of(CLOSURE), List.of(), 0, 0));

        List<String> details = viewModel.getState().getDetails();
        assertTrue(details.get(0).contains("no longer available because it used this connection"));
        assertTrue(details.get(details.size() - 1).startsWith("No alternative route"));
    }

    @Test
    void unaffectedRouteIsShownAsStillAvailable() {
        presenter.prepareDisruptedView(output(Outcome.UNCHANGED, List.of(CLOSURE), List.of("Kipling", "Jane"), 8, 0));

        List<String> details = viewModel.getState().getDetails();
        assertEquals("Your route does not use this connection, so it is still available.", details.get(0));
        assertEquals("Current route: about 8 min · 0 transfers", details.get(2));
    }

    @Test
    void disruptionWithoutAJourneyAsksForASearch() {
        presenter.prepareDisruptedView(output(Outcome.NO_JOURNEY, List.of(CLOSURE), List.of(), 0, 0));

        assertEquals(List.of("Find a route to see how this disruption affects your journey."),
                viewModel.getState().getDetails());
    }

    @Test
    void restoredConnectionShowsTheCurrentRoute() {
        presenter.prepareRestoredView(output(Outcome.REROUTED, List.of(), List.of("Finch", "Union"), 31, 1));

        RerouteState state = viewModel.getState();
        assertEquals("Restored: Line 1 between Bloor-Yonge and Wellesley is available again.", state.getHeadline());
        assertEquals(List.of("Your journey can use this connection again.", "",
                "Current route: about 31 min · 1 transfer", "Finch → Union"), state.getDetails());
        assertTrue(state.getActiveDisruptions().isEmpty());
    }

    @Test
    void failureShowsTheErrorAndKeepsTheActiveDisruptions() {
        presenter.prepareDisruptedView(output(Outcome.NO_JOURNEY, List.of(CLOSURE), List.of(), 0, 0));
        presenter.prepareFailView("That connection is not disrupted.");

        RerouteState state = viewModel.getState();
        assertEquals("That connection is not disrupted.", state.getError());
        assertEquals("", state.getHeadline());
        assertEquals(1, state.getActiveDisruptions().size());
        assertEquals(2, notifications);
    }
}
