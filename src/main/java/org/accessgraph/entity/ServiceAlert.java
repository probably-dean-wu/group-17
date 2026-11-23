package org.accessgraph.entity;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A notice from the transit agency that one line has no service along a stretch of stations for some
 * time. The stretch is given as every station in it, the two end stations included.
 */
public final class ServiceAlert {
    private final String line;
    private final Set<String> stationIds;
    private final String description;
    private final Instant start;
    private final Instant end;

    /**
     * @param line the line without service, or an empty string when the notice names no line
     * @param stationIds the stations in the stretch without service
     * @param description the agency's own wording of the notice
     * @param start when the notice takes effect, or null when it is already in effect
     * @param end when the notice stops applying, or null when no end has been announced
     */
    public ServiceAlert(String line, Collection<String> stationIds, String description, Instant start,
                        Instant end) {
        this.line = Objects.requireNonNull(line);
        this.stationIds = Collections.unmodifiableSet(new LinkedHashSet<>(stationIds));
        this.description = Objects.requireNonNull(description);
        this.start = start;
        this.end = end;
    }

    public String getLine() {
        return line;
    }

    public Set<String> getStationIds() {
        return stationIds;
    }

    public String getDescription() {
        return description;
    }

    /** Whether the notice applies at the given moment. Advance notices of later closures do not. */
    public boolean isActiveAt(Instant time) {
        return (start == null || !time.isBefore(start)) && (end == null || time.isBefore(end));
    }

    /**
     * Whether the connection is inside the stretch without service: it is on this line and both of its
     * stations are in the stretch. A connection that only touches an end station is still running.
     */
    public boolean closes(Connection connection) {
        return line.equals(connection.getLine()) && stationIds.contains(connection.getFromId())
                && stationIds.contains(connection.getToId());
    }
}
