package org.accessgraph.use_case.live_alerts;

import java.time.Instant;

/**
 * The journey to recalculate and the moment of the request. The journey's station ids are null when the
 * passenger has not searched for a route yet.
 */
public final class LiveAlertsInputData {
    private final String originId;
    private final String destinationId;
    private final Instant now;

    public LiveAlertsInputData(String originId, String destinationId, Instant now) {
        this.originId = originId;
        this.destinationId = destinationId;
        this.now = now;
    }

    public String getOriginId() {
        return originId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    /** The moment used to decide which alerts are in effect. */
    public Instant getNow() {
        return now;
    }
}
