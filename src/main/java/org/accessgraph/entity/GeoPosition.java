package org.accessgraph.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** A browser/device position with its actual accuracy and observation time. Never persisted. */
public final class GeoPosition {
    private final double latitude;
    private final double longitude;
    private final double accuracyMetres;
    private final Instant observedAt;

    public GeoPosition(double latitude, double longitude, double accuracyMetres, Instant observedAt) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || !Double.isFinite(accuracyMetres) || accuracyMetres < 0 || accuracyMetres > 20000000) {
            throw new IllegalArgumentException("Invalid location coordinates or accuracy.");
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMetres = accuracyMetres;
        this.observedAt = Objects.requireNonNull(observedAt);
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getAccuracyMetres() { return accuracyMetres; }
    public Instant getObservedAt() { return observedAt; }
    public String getCoordinates() { return Double.toString(latitude) + "," + Double.toString(longitude); }

    public boolean isRecent(Instant now) {
        long age = Duration.between(observedAt, now).getSeconds();
        return age >= -5 && age <= 60;
    }
}
