package unified.qol.ui;

import unified.qol.core.CoreRuntime;
import unified.qol.core.FailureRecord;
import unified.qol.core.FeatureState;
import unified.qol.core.compat.FeatureCompatibility;
import java.util.ArrayList;
import java.util.List;

/** Translation keys describing a feature's runtime and compatibility status (UI-2, LC-11, CMP-4). */
public final class FeatureStatus {
    private FeatureStatus() {
    }

    public static List<String> keys(CoreRuntime core, String featureId) {
        var keys = new ArrayList<String>();
        FeatureState state = core.lifecycle().state(featureId);
        boolean enableFailed = core.lifecycle().failure(featureId)
                .map(f -> f.operation() == FailureRecord.Operation.ENABLE)
                .orElse(false);
        switch (state) {
            case ACTIVE -> keys.add(UiKeys.STATUS_ACTIVE);
            case UNKNOWN -> keys.add(UiKeys.STATUS_UNKNOWN);
            case INACTIVE -> {
                if (enableFailed) {
                    keys.add(UiKeys.STATUS_ENABLE_FAILED);
                } else if (!core.compatibility(featureId).defaultSuppressed()) {
                    // A conflict default already explains why the feature is inactive.
                    keys.add(UiKeys.STATUS_INACTIVE);
                }
            }
        }
        if (core.lifecycle().pendingRestart(featureId)) {
            keys.add(UiKeys.STATUS_PENDING_RESTART);
        }
        FeatureCompatibility compat = core.compatibility(featureId);
        if (compat.defaultSuppressed()) {
            keys.add(UiKeys.STATUS_CONFLICT_DEFAULT);
        } else if (compat.playerOverride()) {
            keys.add(UiKeys.STATUS_CONFLICT_OVERRIDE);
        } else if (compat.hasConflicts()) {
            keys.add(UiKeys.STATUS_CONFLICT);
        }
        return keys;
    }

    /** UNKNOWN features offer Retry disable instead of an enable control (LC-11). */
    public static boolean offersRetryDisable(CoreRuntime core, String featureId) {
        return core.lifecycle().state(featureId) == FeatureState.UNKNOWN;
    }
}
