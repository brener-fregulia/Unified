package unified.qol.core.config;

import com.google.gson.JsonObject;

/**
 * Upgrades a configuration document from {@link #fromVersion()} to {@code fromVersion() + 1}.
 * Implementations must keep all data they do not explicitly transform.
 */
public interface Migration {
    int fromVersion();

    /** Transforms {@code root} in place or returns a new root. Must not set {@code schemaVersion}. */
    JsonObject migrate(JsonObject root);
}
