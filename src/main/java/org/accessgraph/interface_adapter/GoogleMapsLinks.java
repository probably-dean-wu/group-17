package org.accessgraph.interface_adapter;

import org.accessgraph.entity.Station;
import org.accessgraph.entity.GeoPosition;
import org.accessgraph.use_case.station_map.MapRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Key-free Google Maps URLs. Coordinates identify stations, not verified accessible entrances. */
public final class GoogleMapsLinks {
    private GoogleMapsLinks() { }

    public static URI station(Station station) {
        return URI.create("https://www.google.com/maps/search/?api=1&query="
                + URLEncoder.encode(station.getCoordinates(), StandardCharsets.UTF_8));
    }

    public static URI journey(GeoPosition origin, String destination) {
        String address = MapRequest.validateDestination(destination);
        if (address.isEmpty()) { throw new IllegalArgumentException("Enter a destination address first."); }
        String url = "https://www.google.com/maps/dir/?api=1&travelmode=transit&destination="
                + URLEncoder.encode(address, StandardCharsets.UTF_8);
        if (origin != null) {
            url += "&origin=" + URLEncoder.encode(origin.getCoordinates(), StandardCharsets.UTF_8);
        }
        return URI.create(url);
    }
}
