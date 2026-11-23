package org.accessgraph.entity;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessibilityProfileTest {
    private static final Station CONFIRMED = station(Station.Accessibility.CONFIRMED);
    private static final Station LIMITED = station(Station.Accessibility.LIMITED);
    private static final Station UNKNOWN = station(Station.Accessibility.UNKNOWN);

    private static Station station(Station.Accessibility accessibility) {
        return new Station("1", "Station", 43.6, -79.4, accessibility, URI.create("https://www.ttc.ca/x"),
                LocalDate.of(2026, 11, 9));
    }

    @Test
    void wheelchairProfileUsesOnlyConfirmedStations() {
        AccessibilityProfile profile = new AccessibilityProfile(true, false, false, 2);
        assertTrue(profile.isStepFreeRequired());
        assertTrue(profile.canUse(CONFIRMED));
        assertFalse(profile.canUse(LIMITED));
        assertFalse(profile.canUse(UNKNOWN));
    }

    @Test
    void stairsAllowedCanUseEveryStation() {
        AccessibilityProfile profile = new AccessibilityProfile(false, true, false, 2);
        assertFalse(profile.isStepFreeRequired());
        assertTrue(profile.canUse(CONFIRMED));
        assertTrue(profile.canUse(LIMITED));
        assertTrue(profile.canUse(UNKNOWN));
    }

    @Test
    void escalatorsWithoutStairsStayOnConfirmedStationsBecauseTheDataHasNoEscalators() {
        AccessibilityProfile profile = new AccessibilityProfile(false, false, true, 2);
        assertTrue(profile.isStepFreeRequired());
        assertTrue(profile.canUse(CONFIRMED));
        assertFalse(profile.canUse(LIMITED));
        assertFalse(profile.canUse(UNKNOWN));
    }

    @Test
    void wheelchairProfileCannotAllowStairsOrEscalators() {
        assertThrows(IllegalArgumentException.class, () -> new AccessibilityProfile(true, true, false, 2));
        assertThrows(IllegalArgumentException.class, () -> new AccessibilityProfile(true, false, true, 2));
    }

    @Test
    void transferLimitMustBeInRange() {
        assertThrows(IllegalArgumentException.class, () -> new AccessibilityProfile(false, true, true, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new AccessibilityProfile(false, true, true, AccessibilityProfile.MAX_TRANSFERS + 1));
        assertEquals(AccessibilityProfile.MAX_TRANSFERS,
                new AccessibilityProfile(false, true, true, AccessibilityProfile.MAX_TRANSFERS).getMaxTransfers());
    }

    @Test
    void shorthandConstructorKeepsItsMeaning() {
        AccessibilityProfile stepFree = new AccessibilityProfile(true, 2);
        assertTrue(stepFree.isWheelchairRequired());
        assertTrue(stepFree.isStepFreeRequired());
        assertFalse(stepFree.canUse(LIMITED));

        AccessibilityProfile any = new AccessibilityProfile(false, 0);
        assertTrue(any.isStairsAllowed());
        assertTrue(any.isEscalatorsAllowed());
        assertTrue(any.canUse(LIMITED));
        assertEquals(0, any.getMaxTransfers());
    }

    @Test
    void strictestIsAWheelchairProfileWithTwoTransfers() {
        AccessibilityProfile profile = AccessibilityProfile.strictest();
        assertTrue(profile.isWheelchairRequired());
        assertFalse(profile.isStairsAllowed());
        assertFalse(profile.isEscalatorsAllowed());
        assertEquals(2, profile.getMaxTransfers());
    }
}
