package unified.qol.core;

/** Result of one lifecycle action, for reporting to the player (M0 §4.4, TX-8). */
public record TransitionOutcome(String featureId, Kind kind, FeatureState state) {
    public enum Kind {
        ENABLED,
        DISABLED,
        ENABLE_FAILED,
        DISABLE_FAILED,
        /** Enable requested while {@link FeatureState#UNKNOWN}; nothing was called. */
        REJECTED_UNKNOWN,
        /** A restart-required change was recorded but not applied. */
        PENDING_RESTART
    }

    public boolean failed() {
        return kind == Kind.ENABLE_FAILED || kind == Kind.DISABLE_FAILED || kind == Kind.REJECTED_UNKNOWN;
    }
}
