package org.accessgraph.use_case.station_map;

import org.accessgraph.entity.GeoPosition;
import org.accessgraph.entity.Station;

import java.util.List;

/** Map display inputs only; no route, geocoding result, or accessibility assessment is implied. */
public final class MapRequest {
    private final List<Station> stations;
    private final Station focus;
    private final GeoPosition position;
    private final String destination;

    public MapRequest(List<Station> stations, Station focus, GeoPosition position, String destination) {
        this.stations = List.copyOf(stations);
        if (focus != null && !stations.contains(focus)) {
            throw new IllegalArgumentException("Select a station from the displayed list.");
        }
        this.focus = focus;
        this.position = position;
        this.destination = validateDestination(destination);
        if (stations.isEmpty() && position == null && this.destination.isEmpty()) {
            throw new IllegalArgumentException("Choose a station, location, or destination to display.");
        }
    }

    public static String validateDestination(String value) {
        String address = value == null ? "" : value.trim();
        if (address.length() > 200 || address.indexOf('|') >= 0
                || address.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Use an address of up to 200 characters without line breaks or |.");
        }
        return address;
    }

    public List<Station> getStations() { return stations; }
    public Station getFocus() { return focus; }
    public GeoPosition getPosition() { return position; }
    public String getDestination() { return destination; }
}
