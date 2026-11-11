package org.accessgraph.interface_adapter;

import org.accessgraph.use_case.settings.ApiKeyStore;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.Objects;

/** Observable credential configuration. Changes carry a revision number, never a credential value. */
public final class ApiKeySettings {
    public enum Source { NONE, ENVIRONMENT, KEYCHAIN, SESSION }

    private final ApiKeyStore store;
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private volatile String activeKey;
    private volatile Source source;
    private long revision;

    public ApiKeySettings(String environmentKey, ApiKeyStore store) {
        this.store = Objects.requireNonNull(store);
        activeKey = environmentKey == null ? "" : environmentKey.trim();
        source = activeKey.isEmpty() ? Source.NONE : Source.ENVIRONMENT;
    }

    /** For request construction only. Do not put this value into UI text, events, logs, or toString. */
    public String keyForRequest() { return activeKey; }
    public Source getSource() { return source; }
    public boolean isConfigured() { return !activeKey.isEmpty(); }
    public boolean canRemember() { return store.isAvailable(); }

    public void addChangeListener(PropertyChangeListener listener) { changes.addPropertyChangeListener(listener); }

    /** Potentially blocking storage methods must be called outside Swing's event dispatch thread. */
    public synchronized void loadRemembered() throws IOException {
        String saved = store.load();
        if (!saved.isEmpty()) {
            validate(saved);
            apply(saved, Source.KEYCHAIN);
        }
    }

    public synchronized void save(char[] input, boolean remember) throws IOException {
        String key = new String(input).trim();
        validate(key);
        if (remember) {
            if (!store.isAvailable()) { throw new IOException("Secure remembering is unavailable."); }
            store.save(key);
        }
        apply(key, remember ? Source.KEYCHAIN : Source.SESSION);
    }

    public synchronized void clear() throws IOException {
        // Disabling maps must not depend on a successful Keychain unlock or deletion.
        apply("", Source.NONE);
        store.delete();
    }

    public String description() {
        switch (source) {
            case ENVIRONMENT: return "Google Maps is configured from your environment.";
            case KEYCHAIN: return "Google Maps key is saved in macOS Keychain.";
            case SESSION: return "Google Maps key is configured for this session only.";
            default: return "No Google Maps API key is configured.";
        }
    }

    private void apply(String value, Source newSource) {
        activeKey = value;
        source = newSource;
        changes.firePropertyChange("configuration", null, ++revision);
    }

    private void validate(String key) {
        if (!key.matches("[A-Za-z0-9_-]{10,200}")) {
            throw new IllegalArgumentException("Paste a Google Maps API key containing 10–200 letters, digits, - or _.");
        }
    }

    public static ApiKeySettings sessionOnly(String initialKey) {
        return new ApiKeySettings(initialKey, new ApiKeyStore() {
            @Override public boolean isAvailable() { return false; }
            @Override public String load() { return ""; }
            @Override public void save(String value) throws IOException { throw new IOException("Secure storage unavailable."); }
            @Override public void delete() { }
        });
    }
}
