package org.accessgraph.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable record of the EXACT route used when a trip was saved (not a live routing result). */
public final class RouteSnapshot {
    private final List<String> stationIds;
    private final List<String> legLines;
    private final int totalMinutes;
    private final int transfers;

    public RouteSnapshot(List<String> stationIds, List<String> legLines, int totalMinutes, int transfers) {
        Objects.requireNonNull(stationIds);
        Objects.requireNonNull(legLines);
        if (stationIds.size() < 2 || legLines.size() != stationIds.size() - 1
                || totalMinutes < 0 || transfers < 0) {
            throw new IllegalArgumentException("Invalid saved route snapshot.");
        }
        for (String station : stationIds) {
            requireText(station);
        }
        for (String line : legLines) {
            requireText(line);
        }
        this.stationIds = List.copyOf(stationIds);
        this.legLines = List.copyOf(legLines);
        this.totalMinutes = totalMinutes;
        this.transfers = transfers;
    }

    public static RouteSnapshot fromRoute(Route route) {
        List<String> ids = new ArrayList<>();
        for (Station station : route.getStations()) {
            ids.add(station.getId());
        }
        return new RouteSnapshot(ids, route.getLegLines(), route.getTotalMinutes(), route.getTransfers());
    }

    private static void requireText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot entries cannot be empty.");
        }
    }

    public List<String> getStationIds() { return stationIds; }
    public List<String> getLegLines() { return legLines; }
    public int getTotalMinutes() { return totalMinutes; }
    public int getTransfers() { return transfers; }
}
