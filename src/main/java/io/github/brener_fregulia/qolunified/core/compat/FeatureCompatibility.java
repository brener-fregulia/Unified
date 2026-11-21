package io.github.brener_fregulia.qolunified.core.compat;

import java.util.List;

/**
 * Compatibility status of one feature for the settings UI (CMP-4, CMP-5).
 *
 * @param defaultSuppressed the feature is disabled only because of a conflict (no explicit choice)
 * @param playerOverride the player explicitly enabled the feature despite a conflict
 */
public record FeatureCompatibility(String featureId, List<DetectedConflict> conflicts, boolean defaultSuppressed,
        boolean playerOverride) {
    public FeatureCompatibility {
        conflicts = List.copyOf(conflicts);
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}
