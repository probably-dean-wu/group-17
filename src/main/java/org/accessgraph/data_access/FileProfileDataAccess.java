package org.accessgraph.data_access;

import org.accessgraph.entity.AccessibilityProfile;
import org.accessgraph.use_case.manage_profile.ManageProfileDataAccessInterface;
import org.accessgraph.use_case.manage_profile.ProfileDataException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

/**
 * Keeps the accessibility profile in a properties file in the user's home folder, so it survives a restart.
 * The profile that searches use is held in memory and starts as the profile given to the constructor.
 */
public final class FileProfileDataAccess implements ManageProfileDataAccessInterface {
    private static final String VERSION = "version";
    private static final String WHEELCHAIR = "wheelchairRequired";
    private static final String STAIRS = "stairsAllowed";
    private static final String ESCALATORS = "escalatorsAllowed";
    private static final String TRANSFERS = "maxTransfers";
    private static final String CURRENT_VERSION = "1";
    /** How the file is named in messages, so the window never shows the account name in the full path. */
    private static final String DISPLAY_NAME = "~/.accessgraph/profile.properties";
    /** A saved profile is about 200 bytes. Anything far larger is not a profile and is not read in full. */
    private static final int MAX_BYTES = 4096;
    private static final int MAX_SHOWN = 40;

    private final Path file;
    private AccessibilityProfile profile;

    /** Uses {@code ~/.accessgraph/profile.properties}. */
    public FileProfileDataAccess(AccessibilityProfile startingProfile) {
        this(Path.of(System.getProperty("user.home"), ".accessgraph", "profile.properties"), startingProfile);
    }

    FileProfileDataAccess(Path file, AccessibilityProfile startingProfile) {
        this.file = file.toAbsolutePath();
        this.profile = startingProfile;
    }

    @Override
    public AccessibilityProfile getProfile() {
        return profile;
    }

    @Override
    public void setProfile(AccessibilityProfile profile) {
        this.profile = profile;
    }

    @Override
    public Optional<AccessibilityProfile> readSaved() throws ProfileDataException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        // A pipe or device file could block the read, so only an ordinary file is opened.
        if (!Files.isRegularFile(file)) {
            throw new ProfileDataException("your profile file is not a regular file.");
        }
        Properties values = new Properties();
        try {
            byte[] content;
            try (InputStream input = Files.newInputStream(file)) {
                content = input.readNBytes(MAX_BYTES + 1);
            }
            if (content.length > MAX_BYTES) {
                throw new ProfileDataException("your profile file is too large to be a profile.");
            }
            values.load(new StringReader(new String(content, StandardCharsets.UTF_8)));
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProfileDataException("the file " + DISPLAY_NAME + " could not be read.", exception);
        }
        if (!CURRENT_VERSION.equals(require(values, VERSION))) {
            throw new ProfileDataException("it was saved in a format this version of AccessGraph does not know.");
        }
        try {
            return Optional.of(new AccessibilityProfile(bool(values, WHEELCHAIR), bool(values, STAIRS),
                    bool(values, ESCALATORS), whole(values, TRANSFERS)));
        } catch (IllegalArgumentException exception) {
            throw new ProfileDataException(exception.getMessage(), exception);
        }
    }

    /** Writes a temporary file and moves it into place, so a failed save never leaves a half-written profile. */
    @Override
    public void writeSaved(AccessibilityProfile profile) throws ProfileDataException {
        Path directory = file.getParent();
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "profile-", ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    writer.write("# AccessGraph accessibility profile. Change it in the app's Profile tab.\n");
                    writer.write(VERSION + "=" + CURRENT_VERSION + "\n");
                    writer.write(WHEELCHAIR + "=" + profile.isWheelchairRequired() + "\n");
                    writer.write(STAIRS + "=" + profile.isStairsAllowed() + "\n");
                    writer.write(ESCALATORS + "=" + profile.isEscalatorsAllowed() + "\n");
                    writer.write(TRANSFERS + "=" + profile.getMaxTransfers() + "\n");
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new ProfileDataException("the file " + DISPLAY_NAME + " could not be written.", exception);
        }
    }

    private static String require(Properties values, String key) throws ProfileDataException {
        String value = values.getProperty(key);
        if (value == null) {
            throw new ProfileDataException("the setting \"" + key + "\" is missing.");
        }
        return value.trim();
    }

    /** Accepts only "true" or "false", unlike {@link Boolean#parseBoolean}, which reads anything else as false. */
    private static boolean bool(Properties values, String key) throws ProfileDataException {
        String value = require(values, key);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new ProfileDataException("the setting \"" + key + "\" is \"" + shown(value)
                + "\" but must be true or false.");
    }

    private static int whole(Properties values, String key) throws ProfileDataException {
        String value = require(values, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ProfileDataException("the setting \"" + key + "\" is \"" + shown(value)
                    + "\" but must be a whole number.", exception);
        }
    }

    /** A value from the file as it appears in a message, cut short so a long value cannot fill the window. */
    private static String shown(String value) {
        return value.length() <= MAX_SHOWN ? value : value.substring(0, MAX_SHOWN) + "…";
    }
}
