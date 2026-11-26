package unified.qol.core.config;

/** Outcome of loading the configuration file. */
public enum LoadStatus {
    /** File loaded at the current schema version. */
    LOADED,
    /** No file; defaults are used and the file is created on first save. */
    MISSING,
    /** Older file migrated in memory after a backup; written on next save. */
    MIGRATED,
    /** Unreadable content backed up; defaults are used and the file is replaced on next save. */
    RECOVERED,
    /** Corrupt or unmigratable content that could not be backed up; read-only. */
    RECOVERY_BLOCKED,
    /** Migration failed or is missing; read-only, defaults are used. */
    MIGRATION_FAILED,
    /** The file could not be read; read-only, defaults are used. */
    READ_FAILED,
    /** Written by a newer schema version; read-only, understood values are used. */
    NEWER_VERSION;

    public boolean readOnly() {
        return this == RECOVERY_BLOCKED || this == MIGRATION_FAILED || this == READ_FAILED || this == NEWER_VERSION;
    }
}
