package io.github.brener_fregulia.qolunified.core.compat;

import io.github.brener_fregulia.qolunified.core.ConflictDescriptor;
import io.github.brener_fregulia.qolunified.core.Feature;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import io.github.brener_fregulia.qolunified.core.config.FeatureChoice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Installed conflicts per feature, detected once at startup (CMP-1–CMP-6), and the default rule
 * derived from them. Only conflicts declared in descriptors are checked; nothing is modified.
 */
public final class CompatibilityReport {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified");

    private final Map<String, List<DetectedConflict>> conflicts;

    private CompatibilityReport(Map<String, List<DetectedConflict>> conflicts) {
        this.conflicts = conflicts;
    }

    /**
     * Checks every declared conflict. Ordering follows registry order, then declaration order;
     * a mod declared twice for one feature is reported once. A failing presence check is logged and
     * treated as not detected, without affecting other checks.
     */
    public static CompatibilityReport detect(FeatureRegistry registry, ModPresence presence) {
        var result = new LinkedHashMap<String, List<DetectedConflict>>();
        for (Feature feature : registry.features()) {
            String featureId = feature.descriptor().id();
            var found = new ArrayList<DetectedConflict>();
            var seen = new HashSet<String>();
            for (ConflictDescriptor conflict : feature.descriptor().conflicts()) {
                if (!seen.add(conflict.modId())) {
                    continue;
                }
                boolean loaded;
                try {
                    loaded = presence.isLoaded(conflict.modId());
                } catch (RuntimeException e) {
                    LOGGER.warn("Could not check whether mod {} (conflict of feature {}) is installed; "
                            + "treating it as absent", conflict.modId(), featureId, e);
                    continue;
                }
                if (loaded) {
                    found.add(new DetectedConflict(featureId, conflict.modId(), conflict.reasonKey()));
                }
            }
            if (!found.isEmpty()) {
                result.put(featureId, List.copyOf(found));
            }
        }
        return new CompatibilityReport(Collections.unmodifiableMap(result));
    }

    /** Detected conflicts of one feature, in declaration order; empty if none. */
    public List<DetectedConflict> conflicts(String featureId) {
        return conflicts.getOrDefault(featureId, List.of());
    }

    /** All detected conflicts, in registry order. */
    public List<DetectedConflict> all() {
        return conflicts.values().stream().flatMap(List::stream).toList();
    }

    /**
     * Applies CMP-2/CMP-3: without an explicit choice, a conflicting feature defaults to disabled.
     * Explicit choices are returned unchanged. The configuration itself is never modified.
     */
    public FeatureChoice resolve(String featureId, FeatureChoice choice) {
        if (!choice.explicitEnabled() && !conflicts(featureId).isEmpty()) {
            return new FeatureChoice(false, false, choice.settings());
        }
        return choice;
    }

    /** UI-facing status for a feature given its configured (unresolved) choice. */
    public FeatureCompatibility status(String featureId, FeatureChoice choice) {
        List<DetectedConflict> found = conflicts(featureId);
        boolean conflicting = !found.isEmpty();
        return new FeatureCompatibility(featureId, found,
                conflicting && !choice.explicitEnabled(),
                conflicting && choice.explicitEnabled() && choice.enabled());
    }

    /** Logs each detected conflict once, with the startup outcome for its feature. */
    void log(Map<String, FeatureChoice> configured) {
        for (DetectedConflict conflict : all()) {
            FeatureChoice choice = configured.get(conflict.featureId());
            String outcome = !choice.explicitEnabled() ? "disabled by default"
                    : choice.enabled() ? "enabled by explicit player choice" : "disabled by player choice";
            LOGGER.warn("Feature {} conflicts with installed mod {}; {}", conflict.featureId(), conflict.modId(),
                    outcome);
        }
    }

    /** Detects, logs and returns the report. */
    public static CompatibilityReport detectAndLog(FeatureRegistry registry, ModPresence presence,
            Map<String, FeatureChoice> configured) {
        CompatibilityReport report = detect(registry, presence);
        report.log(configured);
        return report;
    }
}
