package org.accessgraph.interface_adapter;

import org.accessgraph.use_case.settings.ApiKeyStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeySettingsTest {
    @Test
    void rememberedKeyOverridesEnvironmentAndSessionReplacementDoesNotPersist() throws Exception {
        FakeStore store = new FakeStore();
        store.value = "remembered-test-key";
        ApiKeySettings settings = new ApiKeySettings("environment-test-key", store);
        settings.loadRemembered();
        assertEquals(ApiKeySettings.Source.KEYCHAIN, settings.getSource());
        assertEquals("remembered-test-key", settings.keyForRequest());
        settings.save("session-test-key".toCharArray(), false);
        assertEquals(ApiKeySettings.Source.SESSION, settings.getSource());
        assertEquals("session-test-key", settings.keyForRequest());
        assertEquals("remembered-test-key", store.value, "Session keys must not silently overwrite saved credentials.");
        ApiKeySettings restarted = new ApiKeySettings("", store);
        restarted.loadRemembered();
        assertEquals("remembered-test-key", restarted.keyForRequest());
    }

    @Test
    void savesAndRemovesRememberedKeysWithoutPuttingSecretsIntoEventsOrDescriptions() throws Exception {
        FakeStore store = new FakeStore();
        ApiKeySettings settings = new ApiKeySettings("", store);
        List<Object> events = new ArrayList<>();
        settings.addChangeListener(event -> events.add(event.getNewValue()));
        settings.save("remembered-test-key".toCharArray(), true);
        assertEquals("remembered-test-key", store.value);
        assertTrue(settings.isConfigured());
        assertFalse(settings.description().contains("remembered-test-key"));
        assertFalse(settings.toString().contains("remembered-test-key"));
        assertTrue(events.stream().allMatch(Long.class::isInstance));
        settings.clear();
        assertFalse(settings.isConfigured());
        assertEquals("", store.value);
    }

    @Test
    void lockedStorageCannotPreventSessionUseOrDisablingMaps() throws Exception {
        FakeStore store = new FakeStore();
        store.fail = true;
        ApiKeySettings settings = new ApiKeySettings("environment-test-key", store);
        assertThrows(IOException.class, () -> settings.save("replacement-test-key".toCharArray(), true));
        assertEquals("environment-test-key", settings.keyForRequest());
        settings.save("session-test-key".toCharArray(), false);
        assertEquals("session-test-key", settings.keyForRequest());
        assertThrows(IOException.class, settings::clear);
        assertFalse(settings.isConfigured(), "Remove disables the current key even if secure deletion fails.");
    }

    @Test
    void invalidOrEmptyKeysDoNotReplaceTheCurrentConfiguration() {
        ApiKeySettings settings = ApiKeySettings.sessionOnly("current-test-key");
        for (String invalid : List.of("", "short", "key with whitespace", "key\ncommand", "x".repeat(201))) {
            assertThrows(IllegalArgumentException.class, () -> settings.save(invalid.toCharArray(), false));
            assertEquals("current-test-key", settings.keyForRequest());
        }
    }

    private static final class FakeStore implements ApiKeyStore {
        private String value = "";
        private boolean fail;
        @Override public boolean isAvailable() { return true; }
        @Override public String load() throws IOException { check(); return value; }
        @Override public void save(String key) throws IOException { check(); value = key; }
        @Override public void delete() throws IOException { check(); value = ""; }
        private void check() throws IOException { if (fail) { throw new IOException("Simulated locked storage"); } }
    }
}
