package io.github.brener_fregulia.qolunified.core;

/** Effective lifecycle state of a feature (M0 §4.2). */
public enum FeatureState {
    /** The last {@code enable()} returned normally. */
    ACTIVE,
    /** Never enabled, or the last {@code disable()} (or rollback) returned normally. */
    INACTIVE,
    /** A {@code disable()} threw; the feature may be fully or partly active. Never treated as inactive. */
    UNKNOWN
}
