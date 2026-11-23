package org.accessgraph.use_case.savedTrip;

import org.accessgraph.entity.SavedTrip;
import java.io.IOException;
import java.util.List;

/** Persistence gateway for saved journeys. The use case knows nothing about the storage format. */
public interface SavedTripDataAccessInterface {
    List<SavedTrip> findAll() throws IOException;
    void save(SavedTrip trip) throws IOException;
    void delete(String tripId) throws IOException;
}
