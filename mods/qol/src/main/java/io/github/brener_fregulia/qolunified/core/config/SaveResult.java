package io.github.brener_fregulia.qolunified.core.config;

/** Outcome of a save request (TX-4, TX-5, TX-9). */
public enum SaveResult {
    SAVED,
    /** Nothing changed and the file is up to date; nothing was written. */
    NO_CHANGE,
    /** Persistence is read-only; nothing was written. */
    READ_ONLY,
    /** Writing failed; the original file is unchanged. */
    FAILED;

    public boolean persisted() {
        return this == SAVED || this == NO_CHANGE;
    }
}
