package io.github.brener_fregulia.qolunified.core.config;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Sequential migrations up to a current schema version. */
public final class Migrations {
    /** Production schema baseline. */
    public static final int CURRENT_VERSION = 1;

    private final int currentVersion;
    private final Map<Integer, Migration> byFromVersion = new HashMap<>();

    public Migrations(int currentVersion, List<Migration> migrations) {
        if (currentVersion < 1) {
            throw new IllegalArgumentException("Schema version must be at least 1");
        }
        this.currentVersion = currentVersion;
        for (Migration migration : migrations) {
            int from = migration.fromVersion();
            if (from < 1 || from >= currentVersion || byFromVersion.putIfAbsent(from, migration) != null) {
                throw new IllegalArgumentException("Invalid or duplicate migration from version " + from);
            }
        }
    }

    public static Migrations production() {
        return new Migrations(CURRENT_VERSION, List.of());
    }

    public int currentVersion() {
        return currentVersion;
    }

    /**
     * Migrates a deep copy of {@code root} from {@code version} to the current version.
     *
     * @throws MigrationException if a step is missing or fails
     */
    JsonObject migrate(JsonObject root, int version) throws MigrationException {
        JsonObject current = root.deepCopy();
        for (int v = version; v < currentVersion; v++) {
            Migration step = byFromVersion.get(v);
            if (step == null) {
                throw new MigrationException("No migration from schema version " + v, null);
            }
            try {
                current = step.migrate(current);
            } catch (RuntimeException e) {
                throw new MigrationException("Migration from schema version " + v + " failed", e);
            }
            if (current == null) {
                throw new MigrationException("Migration from schema version " + v + " returned nothing", null);
            }
            current.addProperty(ConfigurationService.SCHEMA_VERSION, v + 1);
        }
        return current;
    }

    static final class MigrationException extends Exception {
        MigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
