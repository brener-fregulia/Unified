package unified.qol.ui.screen;

import unified.qol.core.CoreRuntime;
import unified.qol.core.TransitionOutcome;
import unified.qol.ui.FeatureStatus;
import unified.qol.ui.UiKeys;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Builds translated components for the screens. */
final class Texts {
    private Texts() {
    }

    static Component status(CoreRuntime core, String featureId) {
        var parts = FeatureStatus.keys(core, featureId).stream().map(key -> {
            if (key.equals(UiKeys.STATUS_CONFLICT_DEFAULT) || key.equals(UiKeys.STATUS_CONFLICT_OVERRIDE)
                    || key.equals(UiKeys.STATUS_CONFLICT)) {
                String mods = core.compatibility(featureId).conflicts().stream()
                        .map(c -> c.modId()).collect(Collectors.joining(", "));
                return I18n.get(key, mods);
            }
            return I18n.get(key);
        }).toList();
        return Component.literal(String.join(" · ", parts));
    }

    /** True when the status is more than plain Active/Inactive. */
    static boolean needsAttention(CoreRuntime core, String featureId) {
        return FeatureStatus.keys(core, featureId).stream()
                .anyMatch(k -> !k.equals(UiKeys.STATUS_ACTIVE) && !k.equals(UiKeys.STATUS_INACTIVE));
    }

    static Component conflictReasons(CoreRuntime core, String featureId) {
        return Component.literal(core.compatibility(featureId).conflicts().stream()
                .map(c -> c.modId() + ": " + I18n.get(c.reasonKey()))
                .collect(Collectors.joining("\n")));
    }

    static Component featureNames(CoreRuntime core, List<TransitionOutcome> outcomes) {
        return Component.literal(outcomes.stream()
                .map(o -> I18n.get(core.registry().require(o.featureId()).descriptor().nameKey()))
                .distinct()
                .collect(Collectors.joining(", ")));
    }
}
