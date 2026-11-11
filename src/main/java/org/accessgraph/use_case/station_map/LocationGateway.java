package org.accessgraph.use_case.station_map;

import org.accessgraph.entity.GeoPosition;

import java.io.IOException;
import java.net.URI;

/** Device location requires explicit browser permission. Callbacks may arrive off the Swing thread. */
public interface LocationGateway {
    enum Failure { PERMISSION_DENIED, UNAVAILABLE, TIMEOUT, STOPPED }

    interface Listener {
        void onPosition(GeoPosition position);
        void onFailure(Failure failure);
    }

    /** Starts a local permission session; the caller opens the returned page in the user's browser. */
    URI start(Listener listener) throws IOException;

    void stop();
}
