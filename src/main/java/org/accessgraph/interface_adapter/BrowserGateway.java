package org.accessgraph.interface_adapter;

import java.io.IOException;
import java.net.URI;

@FunctionalInterface
public interface BrowserGateway {
    void open(URI uri) throws IOException;
}
