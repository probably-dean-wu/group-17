package org.accessgraph.use_case.station_map;

import org.accessgraph.entity.Station;

import java.io.IOException;
import java.util.List;

/** Encoded image boundary; neither Swing components nor Google response types cross it. */
public interface MapImageGateway {
    boolean isConfigured();

    /** A null focus fits every station; otherwise the map focuses on that station. */
    byte[] fetch(List<Station> stations, Station focus) throws IOException, InterruptedException;

    /** Adapters must explicitly support journey pins rather than silently omitting them. */
    default byte[] fetch(MapRequest request) throws IOException, InterruptedException {
        if (request.getPosition() != null || !request.getDestination().isEmpty()) {
            throw new IOException("This map provider does not support location or address pins.");
        }
        return fetch(request.getStations(), request.getFocus());
    }
}
