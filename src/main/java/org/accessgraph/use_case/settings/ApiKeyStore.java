package org.accessgraph.use_case.settings;

import java.io.IOException;

/** Storage for AccessGraph's Google Maps credential only. Implementations must not log secrets. */
public interface ApiKeyStore {
    boolean isAvailable();
    String load() throws IOException;
    void save(String key) throws IOException;
    void delete() throws IOException;
}
