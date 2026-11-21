package io.github.brener_fregulia.qolunified.ui.screen;

import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.TransitionOutcome;
import io.github.brener_fregulia.qolunified.ui.FeatureStatus;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
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
