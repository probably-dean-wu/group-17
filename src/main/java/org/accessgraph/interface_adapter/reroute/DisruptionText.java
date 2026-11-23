package org.accessgraph.interface_adapter.reroute;

import org.accessgraph.use_case.reroute.DisruptedConnection;

import java.util.ArrayList;
import java.util.List;

/** Wording shared by the presenters that write to the disruption view. */
final class DisruptionText {
    private DisruptionText() { }

    /** One row per active disruption, labelled with its line, its stations and where it comes from. */
    static List<RerouteState.ActiveDisruption> rows(List<DisruptedConnection> disruptions) {
        List<RerouteState.ActiveDisruption> rows = new ArrayList<>();
        for (DisruptedConnection disruption : disruptions) {
            rows.add(new RerouteState.ActiveDisruption(disruption.getLine(), disruption.getStationId(),
                    disruption.getOtherStationId(), disruption.getLine() + ": " + disruption.getStationName()
                    + " ↔ " + disruption.getOtherStationName()
                    + (disruption.isLive() ? " (live)" : " (simulated)")));
        }
        return rows;
    }

    /** Adds a route as its own paragraph: a summary line, then the station sequence. */
    static void addRoute(List<String> details, String title, List<String> stationNames, int totalMinutes,
                         int transfers) {
        if (!details.isEmpty()) {
            details.add("");
        }
        details.add(title + ": about " + totalMinutes + " min · " + transfers
                + (transfers == 1 ? " transfer" : " transfers"));
        details.add(String.join(" → ", stationNames));
    }
}
