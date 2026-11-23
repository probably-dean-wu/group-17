package org.accessgraph.entity;

import java.util.Objects;

/** Named origin/destination pair, optionally with the route found when it was saved. */
public final class SavedTrip {
    private final String id;
    private final String name;
    private final String originId;
    private final String destinationId;
    private final RouteSnapshot previousRoute;

    public SavedTrip(String id, String name, String originId, String destinationId,
                     RouteSnapshot previousRoute) {
        this.id = text(id, "Trip ID");
        this.name = text(name, "Trip name");
        this.originId = text(originId, "Origin");
        this.destinationId = text(destinationId, "Destination");
        if (this.originId.equals(this.destinationId)) {
            throw new IllegalArgumentException("Choose two different stations.");
        }
        if (previousRoute != null && (!previousRoute.getStationIds().get(0).equals(this.originId)
                || !previousRoute.getStationIds().get(previousRoute.getStationIds().size() - 1)
                .equals(this.destinationId))) {
            throw new IllegalArgumentException("Saved route endpoints do not match the trip.");
        }
        this.previousRoute = previousRoute;
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty.");
        }
        return value.trim();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getOriginId() { return originId; }
    public String getDestinationId() { return destinationId; }
    public RouteSnapshot getPreviousRoute() { return previousRoute; }
}
