package org.accessgraph.data_access;

import org.accessgraph.entity.RouteSnapshot;
import org.accessgraph.entity.SavedTrip;
import org.accessgraph.use_case.savedTrip.SavedTripDataAccessInterface;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Local persistent storage. Atomic replacement protects against partially written saves. */
public final class FileSavedTripDataAccess implements SavedTripDataAccessInterface {
    private static final int MAX_TRIPS = 10000;
    private static final int MAX_STATIONS = 1000;
    private final Path file;

    public FileSavedTripDataAccess(Path file) {
        this.file = file.toAbsolutePath();
    }

    @Override
    public synchronized List<SavedTrip> findAll() throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        Properties data = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            data.load(in);
        }
        try {
            int count = count(data, "count", MAX_TRIPS);
            List<SavedTrip> result = new ArrayList<>();
            Set<String> uniqueIds = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String key = "trip." + i + ".";
                RouteSnapshot snapshot = null;
                if ("true".equals(required(data, key + "hasSnapshot"))) {
                    int stationsCount = count(data, key + "stationCount", MAX_STATIONS);
                    if (stationsCount < 2) {
                        throw new IllegalArgumentException("Snapshot needs at least two stations.");
                    }
                    List<String> stations = new ArrayList<>();
                    List<String> lines = new ArrayList<>();
                    for (int n = 0; n < stationsCount; n++) {
                        stations.add(required(data, key + "station." + n));
                        if (n != 0) {
                            lines.add(required(data, key + "line." + (n - 1)));
                        }
                    }
                    snapshot = new RouteSnapshot(stations, lines,
                            Integer.parseInt(required(data, key + "minutes")),
                            Integer.parseInt(required(data, key + "transfers")));
                }
                SavedTrip trip = new SavedTrip(required(data, key + "id"), required(data, key + "name"),
                        required(data, key + "origin"), required(data, key + "destination"), snapshot);
                if (!uniqueIds.add(trip.getId())) {
                    throw new IllegalArgumentException("Duplicate trip identifier.");
                }
                result.add(trip);
            }
            return result;
        } catch (RuntimeException exception) {
            throw new IOException("Saved trips file is invalid: " + file, exception);
        }
    }

    @Override
    public synchronized void save(SavedTrip trip) throws IOException {
        List<SavedTrip> items = findAll();
        items.removeIf(existing -> existing.getId().equals(trip.getId()));
        items.add(trip);
        write(items);
    }

    @Override
    public synchronized void delete(String tripId) throws IOException {
        List<SavedTrip> items = findAll();
        if (items.removeIf(existing -> existing.getId().equals(tripId))) {
            write(items);
        }
    }

    private void write(List<SavedTrip> items) throws IOException {
        if (items.size() > MAX_TRIPS) {
            throw new IOException("Too many saved trips.");
        }
        Properties data = new Properties();
        data.setProperty("count", Integer.toString(items.size()));
        for (int i = 0; i < items.size(); i++) {
            SavedTrip trip = items.get(i);
            String key = "trip." + i + ".";
            data.setProperty(key + "id", trip.getId());
            data.setProperty(key + "name", trip.getName());
            data.setProperty(key + "origin", trip.getOriginId());
            data.setProperty(key + "destination", trip.getDestinationId());
            RouteSnapshot snapshot = trip.getPreviousRoute();
            data.setProperty(key + "hasSnapshot", Boolean.toString(snapshot != null));
            if (snapshot != null) {
                data.setProperty(key + "stationCount", Integer.toString(snapshot.getStationIds().size()));
                data.setProperty(key + "minutes", Integer.toString(snapshot.getTotalMinutes()));
                data.setProperty(key + "transfers", Integer.toString(snapshot.getTransfers()));
                for (int n = 0; n < snapshot.getStationIds().size(); n++) {
                    data.setProperty(key + "station." + n, snapshot.getStationIds().get(n));
                    if (n != 0) {
                        data.setProperty(key + "line." + (n - 1), snapshot.getLegLines().get(n - 1));
                    }
                }
            }
        }
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "accessgraph-trips-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                data.store(out, "AccessGraph saved journeys (local device)");
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static int count(Properties data, String key, int max) {
        int value = Integer.parseInt(required(data, key));
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("Invalid count: " + key);
        }
        return value;
    }

    private static String required(Properties data, String key) {
        String value = data.getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing field " + key);
        }
        return value;
    }
}
