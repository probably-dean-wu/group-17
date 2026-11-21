package org.accessgraph.use_case.reroute;

import org.accessgraph.entity.Disruption;
import org.accessgraph.entity.TransitNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * A disruption as plain data the presenter can format: its type, its line, the two stations it lies
 * between, and whether it comes from a live alert or was simulated.
 */
public final class DisruptedConnection {
    private final String typeLabel;
    private final String line;
    private final String stationId;
    private final String stationName;
    private final String otherStationId;
    private final String otherStationName;
    private final boolean live;

    public DisruptedConnection(String typeLabel, String line, String stationId, String stationName,
                               String otherStationId, String otherStationName, boolean live) {
        this.typeLabel = typeLabel;
        this.line = line;
        this.stationId = stationId;
        this.stationName = stationName;
        this.otherStationId = otherStationId;
        this.otherStationName = otherStationName;
        this.live = live;
    }

    /** Describes a disruption, looking up the names of its stations in the network. */
    public static DisruptedConnection of(TransitNetwork network, Disruption disruption) {
        return new DisruptedConnection(disruption.getType().getLabel(), disruption.getLine(),
                disruption.getStationId(), network.getStation(disruption.getStationId()).getName(),
                disruption.getOtherStationId(), network.getStation(disruption.getOtherStationId()).getName(),
                disruption.isLive());
    }

    /** Describes every disruption in the list, in the same order. */
    public static List<DisruptedConnection> listOf(TransitNetwork network, List<Disruption> disruptions) {
        List<DisruptedConnection> described = new ArrayList<>();
        for (Disruption disruption : disruptions) {
            described.add(of(network, disruption));
        }
        return described;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public String getLine() {
        return line;
    }

    public String getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public String getOtherStationId() {
        return otherStationId;
    }

    public String getOtherStationName() {
        return otherStationName;
    }

    /** True when the disruption comes from a live alert, false when the passenger simulated it. */
    public boolean isLive() {
        return live;
    }
}
