package org.accessgraph.data_access;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.entity.Connection;
import org.accessgraph.entity.Station;
import org.accessgraph.entity.TransitNetwork;
import org.accessgraph.use_case.manage_profile.ProfileDataException;
import org.accessgraph.use_case.plan_route.DijkstraRouteFinder;
import org.accessgraph.use_case.plan_route.PlanRouteInputData;
import org.accessgraph.use_case.plan_route.PlanRouteInteractor;
import org.accessgraph.use_case.plan_route.PlanRouteOutputBoundary;
import org.accessgraph.use_case.plan_route.PlanRouteOutputData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileProfileDataAccessTest {
    @TempDir Path directory;

    private Path file() {
        return directory.resolve("profile.properties");
    }

    private FileProfileDataAccess access() {
        return new FileProfileDataAccess(file(), AccessibilityProfile.strictest());
    }

    private void writeFile(String... lines) throws IOException {
        Files.write(file(), List.of(lines), StandardCharsets.UTF_8);
    }

    private static final String[] VALID = {"version=1", "wheelchairRequired=false", "stairsAllowed=true",
        "escalatorsAllowed=false", "maxTransfers=1"};

    @Test
    void savedProfileIsRestoredAfterARestart() throws Exception {
        access().writeSaved(new AccessibilityProfile(false, true, false, 1));

        AccessibilityProfile restored = access().readSaved().orElseThrow();
        assertFalse(restored.isWheelchairRequired());
        assertTrue(restored.isStairsAllowed());
        assertFalse(restored.isEscalatorsAllowed());
        assertEquals(1, restored.getMaxTransfers());
    }

    @Test
    void savingReplacesTheEarlierProfile() throws Exception {
        FileProfileDataAccess access = access();
        access.writeSaved(new AccessibilityProfile(false, true, true, 3));
        access.writeSaved(new AccessibilityProfile(true, false, false, 0));

        AccessibilityProfile restored = access().readSaved().orElseThrow();
        assertTrue(restored.isWheelchairRequired());
        assertEquals(0, restored.getMaxTransfers());
    }

    @Test
    void nothingSavedReadsAsEmpty() throws Exception {
        assertTrue(access().readSaved().isEmpty());
    }

    @Test
    void searchesUseTheStartingProfileUntilAnotherIsSet() {
        FileProfileDataAccess access = access();
        assertTrue(access.getProfile().isWheelchairRequired());
        AccessibilityProfile stairs = new AccessibilityProfile(false, true, true, 2);
        access.setProfile(stairs);
        assertSame(stairs, access.getProfile());
    }

    @Test
    void savingCreatesTheFolder() throws Exception {
        Path nested = directory.resolve("new-folder").resolve("profile.properties");
        new FileProfileDataAccess(nested, AccessibilityProfile.strictest())
                .writeSaved(new AccessibilityProfile(false, true, true, 2));
        assertTrue(Files.exists(nested));
    }

    @Test
    void theFileIsReadableText() throws Exception {
        access().writeSaved(new AccessibilityProfile(false, true, false, 1));
        List<String> lines = Files.readAllLines(file(), StandardCharsets.UTF_8);
        assertEquals(List.of(VALID), lines.subList(1, lines.size()));
    }

    @Test
    void validFileWrittenByHandIsRead() throws Exception {
        writeFile(VALID);
        assertTrue(access().readSaved().orElseThrow().isStairsAllowed());
    }

    @Test
    void settingThatIsNeitherTrueNorFalseIsRejected() throws Exception {
        writeFile("version=1", "wheelchairRequired=false", "stairsAllowed=maybe", "escalatorsAllowed=false",
                "maxTransfers=1");
        ProfileDataException exception = assertThrows(ProfileDataException.class, () -> access().readSaved());
        assertEquals("the setting \"stairsAllowed\" is \"maybe\" but must be true or false.", exception.getMessage());
    }

    @Test
    void transferLimitThatIsNotANumberIsRejected() throws Exception {
        writeFile("version=1", "wheelchairRequired=false", "stairsAllowed=true", "escalatorsAllowed=false",
                "maxTransfers=abc");
        ProfileDataException exception = assertThrows(ProfileDataException.class, () -> access().readSaved());
        assertEquals("the setting \"maxTransfers\" is \"abc\" but must be a whole number.", exception.getMessage());
    }

    @Test
    void missingSettingIsRejected() throws Exception {
        writeFile("version=1", "wheelchairRequired=false", "stairsAllowed=true", "maxTransfers=1");
        ProfileDataException exception = assertThrows(ProfileDataException.class, () -> access().readSaved());
        assertEquals("the setting \"escalatorsAllowed\" is missing.", exception.getMessage());
    }

    @Test
    void valuesTheProfileRejectsAreReported() throws Exception {
        writeFile("version=1", "wheelchairRequired=false", "stairsAllowed=true", "escalatorsAllowed=false",
                "maxTransfers=9");
        ProfileDataException exception = assertThrows(ProfileDataException.class, () -> access().readSaved());
        assertEquals("Maximum transfers cannot be more than 3.", exception.getMessage());

        writeFile("version=1", "wheelchairRequired=true", "stairsAllowed=true", "escalatorsAllowed=false",
                "maxTransfers=1");
        exception = assertThrows(ProfileDataException.class, () -> access().readSaved());
        assertEquals("A wheelchair profile cannot allow stairs or escalators.", exception.getMessage());
    }

    @Test
    void unknownFormatOrUnreadableFileIsRejected() throws Exception {
        writeFile("version=2", "wheelchairRequired=true", "stairsAllowed=false", "escalatorsAllowed=false",
                "maxTransfers=1");
        assertThrows(ProfileDataException.class, () -> access().readSaved());

        writeFile("version=1", "wheelchairRequired=\\uZZZZ");
        ProfileDataException exception = assertThrows(ProfileDataException.class, () -> access().readSaved());
        assertEquals("the file ~/.accessgraph/profile.properties could not be read.", exception.getMessage());

        Files.delete(file());
        Files.createDirectory(file());
        exception = assertThrows(ProfileDataException.class, () -> access().readSaved());
        assertEquals("your profile file is not a regular file.", exception.getMessage());
    }

    @Test
    void fileFarLargerThanAProfileIsNotRead() throws Exception {
        Files.writeString(file(), "a".repeat(5000));
        ProfileDataException exception = assertThrows(ProfileDataException.class, () -> access().readSaved());
        assertEquals("your profile file is too large to be a profile.", exception.getMessage());
    }

    @Test
    void longValuesAreCutShortInMessages() throws Exception {
        writeFile("version=1", "wheelchairRequired=false", "stairsAllowed=" + "x".repeat(200),
                "escalatorsAllowed=false", "maxTransfers=1");
        ProfileDataException exception = assertThrows(ProfileDataException.class, () -> access().readSaved());
        assertEquals("the setting \"stairsAllowed\" is \"" + "x".repeat(40) + "…\" but must be true or false.",
                exception.getMessage());
    }

    @Test
    void aFailedSaveIsReportedAndLeavesNoTemporaryFile() throws Exception {
        Files.createDirectory(file());
        Files.writeString(file().resolve("blocker"), "a folder in the way of the profile file");

        ProfileDataException exception = assertThrows(ProfileDataException.class,
                () -> access().writeSaved(new AccessibilityProfile(false, true, true, 1)));
        assertEquals("the file ~/.accessgraph/profile.properties could not be written.", exception.getMessage());
        assertFalse(exception.getMessage().contains(directory.toString()),
                "Messages never show the full path, which contains the account name.");
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of(file()), files.collect(Collectors.toList()));
        }
    }

    /** Captures the planner's result. */
    private static final class Planner implements PlanRouteOutputBoundary {
        private PlanRouteOutputData success;
        private String error;

        @Override
        public void prepareSuccessView(PlanRouteOutputData outputData) {
            success = outputData;
            error = null;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
            success = null;
        }
    }

    @Test
    void laterSearchesUseTheSavedProfile() throws Exception {
        Station union = new Station("1", "Union", 43.6, -79.4, Station.Accessibility.CONFIRMED,
                URI.create("https://www.ttc.ca/x"), LocalDate.of(2026, 11, 9));
        Station king = new Station("2", "King", 43.6, -79.4, Station.Accessibility.LIMITED,
                URI.create("https://www.ttc.ca/x"), LocalDate.of(2026, 11, 9));
        TransitNetwork network = new TransitNetwork(List.of(union, king), List.of(
                new Connection("2", "1", "Line 1", 2), new Connection("1", "2", "Line 1", 2)));
        FileProfileDataAccess access = access();
        Planner planner = new Planner();
        PlanRouteInteractor interactor =
                new PlanRouteInteractor(() -> network, access, new DijkstraRouteFinder(), planner);

        interactor.execute(new PlanRouteInputData("2", "1"));
        assertNull(planner.success);
        assertTrue(planner.error.startsWith("King is not confirmed as accessible"), planner.error);

        AccessibilityProfile stairs = new AccessibilityProfile(false, true, false, 2);
        access.writeSaved(stairs);
        access.setProfile(stairs);
        interactor.execute(new PlanRouteInputData("2", "1"));
        assertEquals(List.of("King", "Union"), planner.success.getStationNames());

        FileProfileDataAccess restarted = access();
        restarted.setProfile(restarted.readSaved().orElseThrow());
        new PlanRouteInteractor(() -> network, restarted, new DijkstraRouteFinder(), planner)
                .execute(new PlanRouteInputData("2", "1"));
        assertEquals(List.of("King", "Union"), planner.success.getStationNames());
    }
}
