package org.accessgraph.data_access;

import org.accessgraph.use_case.settings.ApiKeyStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Uses the user's macOS Keychain. The save command is passed through stdin, never process arguments. */
public final class MacKeychainApiKeyStore implements ApiKeyStore {
    private static final String TOOL = "/usr/bin/security";
    private static final String ACCOUNT = "org.accessgraph";
    private static final String SERVICE = "AccessGraph Google Maps API key";
    private static final int NOT_FOUND = 44;

    @Override
    public boolean isAvailable() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
                && Files.isExecutable(Path.of(TOOL));
    }

    @Override
    public String load() throws IOException {
        if (!isAvailable()) { return ""; }
        Process process = new ProcessBuilder(TOOL, "find-generic-password", "-a", ACCOUNT, "-s", SERVICE, "-w")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            int exit = await(process);
            if (exit == NOT_FOUND) { return ""; }
            if (exit != 0) { throw new IOException("Could not read the Google Maps key from macOS Keychain."); }
            byte[] bytes = process.getInputStream().readNBytes(1025);
            if (bytes.length > 1024) { throw new IOException("The saved Google Maps key is invalid."); }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } finally {
            process.getInputStream().close();
            process.destroy();
        }
    }

    @Override
    public void save(String key) throws IOException {
        if (!isAvailable()) { throw new IOException("Secure key storage is unavailable on this computer."); }
        if (!key.matches("[A-Za-z0-9_-]{10,200}")) { throw new IOException("Invalid Google Maps key format."); }
        Process process = new ProcessBuilder(TOOL, "-i")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            // The allowed key alphabet cannot add a command, quote, or line break to security's interactive input.
            String command = "add-generic-password -U -a " + ACCOUNT + " -s \"" + SERVICE + "\" -w \"" + key + "\"\n";
            try (java.io.OutputStream input = process.getOutputStream()) {
                input.write(command.getBytes(StandardCharsets.UTF_8));
            }
            if (await(process) != 0 || !key.equals(load())) {
                throw new IOException("Could not save the Google Maps key in macOS Keychain.");
            }
        } finally {
            process.destroy();
        }
    }

    @Override
    public void delete() throws IOException {
        if (!isAvailable()) { return; }
        Process process = new ProcessBuilder(TOOL, "delete-generic-password", "-a", ACCOUNT, "-s", SERVICE)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            int exit = await(process);
            if (exit != 0 && exit != NOT_FOUND) {
                throw new IOException("Could not remove the Google Maps key from macOS Keychain.");
            }
        } finally {
            process.destroy();
        }
    }

    private int await(Process process) throws IOException {
        try {
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("macOS Keychain did not respond. Unlock it and try again.");
            }
            return process.exitValue();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("The Keychain operation was interrupted.");
        }
    }
}
