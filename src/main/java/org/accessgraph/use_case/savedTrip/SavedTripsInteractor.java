package org.accessgraph.use_case.savedTrip;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.entity.Connection;
import org.accessgraph.entity.Route;
import org.accessgraph.entity.RouteSnapshot;
import org.accessgraph.entity.SavedTrip;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.TransitNetwork;
import org.accessgraph.use_case.plan_route.ProfileDataAccessInterface;
import org.accessgraph.use_case.plan_route.RouteFinder;
import org.accessgraph.use_case.plan_route.TransitNetworkDataAccessInterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Save/load/delete trips and compare saved route snapshots with today's network and profile. */
public final class SavedTripsInteractor implements SavedTripsInputBoundary {
    private final SavedTripDataAccessInterface trips;
    private final TransitNetworkDataAccessInterface networkAccess;
    private final ProfileDataAccessInterface profileAccess;
    private final RouteFinder routeFinder;
    private final SavedTripsOutputBoundary presenter;
    private String loadedTripId;

    public SavedTripsInteractor(SavedTripDataAccessInterface trips,
                                TransitNetworkDataAccessInterface networkAccess,
                                ProfileDataAccessInterface profileAccess,
                                RouteFinder routeFinder, SavedTripsOutputBoundary presenter) {
        this.trips = trips;
        this.networkAccess = networkAccess;
        this.profileAccess = profileAccess;
        this.routeFinder = routeFinder;
        this.presenter = presenter;
    }

    @Override
    public void list() {
        try {
            presenter.prepareSuccessView(empty(trips.findAll(), "Select a trip to reload it."));
        } catch (IOException exception) {
            presenter.prepareFailView("Cannot read saved trips: " + exception.getMessage());
        }
    }

    @Override
    public void save(String name, String originId, String destinationId) {
        try {
            TransitNetwork network = networkAccess.getNetwork();
            validate(network, originId, destinationId);
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Enter a name for this trip.");
            }
            RouteSnapshot snapshot = current(network, originId, destinationId)
                    .map(RouteSnapshot::fromRoute).orElse(null);
            SavedTrip trip = new SavedTrip(UUID.randomUUID().toString(), name, originId,
                    destinationId, snapshot);
            trips.save(trip);
            loadedTripId = trip.getId();
            showLoaded(trip, "Trip saved. Route snapshots use the conditions at the time of saving.");
        } catch (IOException exception) {
            presenter.prepareFailView("Cannot save trip: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            presenter.prepareFailView(exception.getMessage());
        }
    }

    @Override
    public void load(String tripId) {
        try {
            SavedTrip trip = requireTrip(tripId);
            loadedTripId = trip.getId();
            showLoaded(trip, "Recalculated using current accessibility and disruption settings.");
        } catch (IOException exception) {
            presenter.prepareFailView("Cannot load trip: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            presenter.prepareFailView(exception.getMessage());
        }
    }

    @Override
    public void delete(String tripId) {
        try {
            requireTrip(tripId);
            trips.delete(tripId);
            if (tripId.equals(loadedTripId)) {
                loadedTripId = null;
            }
            presenter.prepareSuccessView(empty(trips.findAll(), "Trip deleted."));
        } catch (IOException exception) {
            presenter.prepareFailView("Cannot delete trip: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            presenter.prepareFailView(exception.getMessage());
        }
    }

    @Override
    public void refreshLoadedTrip() {
        if (loadedTripId != null) {
            load(loadedTripId);
        }
    }

    private SavedTrip requireTrip(String id) throws IOException {
        return trips.findAll().stream().filter(trip -> trip.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Select an existing saved trip."));
    }

    private void showLoaded(SavedTrip trip, String notice) throws IOException {
        TransitNetwork network = networkAccess.getNetwork();
        RouteSnapshot previous = trip.getPreviousRoute();
        Optional<Route> searched = current(network, trip.getOriginId(), trip.getDestinationId());
        RouteSnapshot currentRoute = searched.map(RouteSnapshot::fromRoute).orElse(null);
        boolean feasible = previous != null && isFeasible(previous, network, profileAccess.getProfile());
        presenter.prepareSuccessView(new SavedTripsOutputData(trips.findAll(), trip, currentRoute,
                previous == null ? List.of() : stationNames(network, previous),
                currentRoute == null ? List.of() : stationNames(network, currentRoute), feasible, notice));
    }

    private static SavedTripsOutputData empty(List<SavedTrip> saved, String notice) {
        return new SavedTripsOutputData(saved, null, null, List.of(), List.of(), false, notice);
    }

    private static void validate(TransitNetwork network, String origin, String destination) {
        if (origin == null || destination == null || !network.hasStation(origin)
                || !network.hasStation(destination)) {
            throw new IllegalArgumentException("Choose two supported stations.");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("Choose two different stations.");
        }
    }

    private Optional<Route> current(TransitNetwork network, String origin, String destination) {
        if (origin == null || destination == null || !network.hasStation(origin)
                || !network.hasStation(destination) || origin.equals(destination)) {
            return Optional.empty();
        }
        AccessibilityProfile profile = profileAccess.getProfile();
        if (!profile.canUse(network.getStation(origin)) || !profile.canUse(network.getStation(destination))) {
            return Optional.empty();
        }
        return routeFinder.findRoute(network, origin, destination, profile);
    }

    /** Validates each saved directed leg AND line, all transfers and accessible boarding/exit points. */
    static boolean isFeasible(RouteSnapshot previous, TransitNetwork network, AccessibilityProfile profile) {
        List<String> stations = previous.getStationIds();
        List<String> lines = previous.getLegLines();
        for (String id : stations) {
            if (!network.hasStation(id)) {
                return false;
            }
        }
        if (!profile.canUse(network.getStation(stations.get(0)))
                || !profile.canUse(network.getStation(stations.get(stations.size() - 1)))) {
            return false;
        }
        int transfers = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0 && !lines.get(i).equals(lines.get(i - 1))) {
                transfers++;
                if (!profile.canUse(network.getStation(stations.get(i)))) {
                    return false;
                }
            }
            boolean connectionAvailable = false;
            for (Connection connection : network.connectionsFrom(stations.get(i))) {
                if (connection.getToId().equals(stations.get(i + 1))
                        && connection.getLine().equals(lines.get(i))) {
                    connectionAvailable = true;
                    break;
                }
            }
            if (!connectionAvailable) {
                return false;
            }
        }
        return transfers <= profile.getMaxTransfers();
    }

    private static List<String> stationNames(TransitNetwork network, RouteSnapshot snapshot) {
        List<String> result = new ArrayList<>();
        for (String id : snapshot.getStationIds()) {
            Station station = network.getStation(id);
            result.add(station == null ? id : station.getName());
        }
        return result;
    }
}
