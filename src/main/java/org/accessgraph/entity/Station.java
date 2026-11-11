package org.accessgraph.entity;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

/** A dated station-level listing, not a guarantee about entrances, platforms, or elevator operation. */
public final class Station {
    public enum Accessibility { CONFIRMED, LIMITED, UNKNOWN }

    private final String id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final Accessibility accessibility;
    private final URI source;
    private final LocalDate checkedOn;

    public Station(String id, String name, double latitude, double longitude,
                   Accessibility accessibility, URI source, LocalDate checkedOn) {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid station identity or coordinates.");
        }
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accessibility = Objects.requireNonNull(accessibility);
        this.source = Objects.requireNonNull(source);
        this.checkedOn = Objects.requireNonNull(checkedOn);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Accessibility getAccessibility() { return accessibility; }
    public URI getSource() { return source; }
    public LocalDate getCheckedOn() { return checkedOn; }
    public String getCoordinates() { return Double.toString(latitude) + "," + Double.toString(longitude); }
}
