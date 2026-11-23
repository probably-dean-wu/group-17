package org.accessgraph.use_case.live_alerts;

import org.accessgraph.entity.ServiceAlert;

import java.io.IOException;
import java.util.List;

/** Supplies the transit agency's published no-service notices. Implemented in the data access layer. */
public interface ServiceAlertDataAccessInterface {
    /**
     * Returns every published notice that takes a stretch of a line out of service. Notices that are not
     * in effect yet are included, so the caller decides which ones apply at a given moment.
     *
     * @throws IOException when the notices cannot be retrieved or read
     */
    List<ServiceAlert> getNoServiceAlerts() throws IOException;
}
