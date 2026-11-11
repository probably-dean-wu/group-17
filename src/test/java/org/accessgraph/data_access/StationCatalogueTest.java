package org.accessgraph.data_access;

import org.accessgraph.entity.Station;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationCatalogueTest {
    @Test
    void catalogueHasUniqueTorontoLocationsAndEvidenceForEveryStation() throws Exception {
        List<Station> stations = new BundledStationRepository().load();
        assertTrue(stations.size() >= 109);
        assertEquals(stations.size(), stations.stream().map(Station::getId).distinct().count());
        for (Station station : stations) {
            assertTrue(station.getLatitude() > 43 && station.getLatitude() < 44);
            assertTrue(station.getLongitude() > -80 && station.getLongitude() < -79);
            assertEquals("www.ttc.ca", station.getSource().getHost());
            assertTrue(station.getSource().getPath().startsWith("/subway-stations/"));
        }
        Set<String> names = stations.stream().map(Station::getName).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("Union", "Humber College", "Mount Dennis", "Finch")));
        assertFalse(names.contains("McCowan"), "Retired Line 3 stations must not become accessible map pins.");
    }

    @Test
    void missingAndConflictingInformationIsNotPromotedToConfirmedAccess() throws Exception {
        List<Station> stations = new BundledStationRepository().load();
        assertEquals(Station.Accessibility.CONFIRMED, named(stations, "Union").getAccessibility());
        assertEquals(Station.Accessibility.LIMITED, named(stations, "Old Mill").getAccessibility());
        assertEquals(Station.Accessibility.UNKNOWN, named(stations, "College").getAccessibility());
        assertEquals(Station.Accessibility.UNKNOWN, named(stations, "Lawrence").getAccessibility());
    }

    private Station named(List<Station> stations, String name) {
        return stations.stream().filter(station -> station.getName().equals(name)).findFirst().orElseThrow();
    }
}
