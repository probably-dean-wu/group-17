package org.accessgraph.use_case.reroute;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.entity.Route;
import org.accessgraph.entity.TransitNetwork;
import org.accessgraph.use_case.plan_route.ProfileDataAccessInterface;
import org.accessgraph.use_case.plan_route.RouteFinder;

import java.util.Optional;

/**
 * Finds the route for the passenger's journey under the disruptions that are active at the moment it is
 * asked. The use cases that change the disruptions ask before and after a change to see what the change
 * did to the journey.
 */
public final class JourneyRecalculator {
    private final ProfileDataAccessInterface profileAccess;
    private final DisruptionDataAccessInterface disruptionAccess;
    private final RouteFinder routeFinder;

    public JourneyRecalculator(ProfileDataAccessInterface profileAccess,
                               DisruptionDataAccessInterface disruptionAccess,
                               RouteFinder routeFinder) {
        this.profileAccess = profileAccess;
        this.disruptionAccess = disruptionAccess;
        this.routeFinder = routeFinder;
    }

    /**
     * Whether the ids name a journey between two different supported stations. They are null when the
     * passenger has not searched for a route yet.
     *
     * @param network the full network, before any disruption is applied
     */
    public boolean hasJourney(TransitNetwork network, String originId, String destinationId) {
        return originId != null && destinationId != null && !originId.equals(destinationId)
                && network.hasStation(originId) && network.hasStation(destinationId);
    }

    /**
     * Returns the journey's route under the disruptions that are active right now. It is empty when there
     * is no journey, when the profile cannot use the origin or destination, or when no route is feasible.
     *
     * @param network the full network, before any disruption is applied
     */
    public Optional<Route> findRoute(TransitNetwork network, String originId, String destinationId) {
        if (!hasJourney(network, originId, destinationId)) {
            return Optional.empty();
        }
        AccessibilityProfile profile = profileAccess.getProfile();
        if (!profile.canUse(network.getStation(originId)) || !profile.canUse(network.getStation(destinationId))) {
            return Optional.empty();
        }
        TransitNetwork available = network.without(disruptionAccess.getActiveDisruptions());
        return routeFinder.findRoute(available, originId, destinationId, profile);
    }
}
