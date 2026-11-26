package unified.qol.feature.mousetweaks;

import unified.qol.core.Feature;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingsSnapshot;
import java.util.Objects;

/**
 * Inventory Mouse Tweaks (mouse-tweaks-v1.md). The screen hooks are registered at startup and act only while this
 * feature is active, so enabling and disabling is LIVE and cannot fail.
 */
public final class MouseTweaksFeature implements Feature {
    private static volatile FeatureContext active;
    private static SettingsSnapshot cachedFrom;
    private static MouseTweaksConfig cached;

    @Override
    public FeatureDescriptor descriptor() {
        return MouseTweaksSettings.DESCRIPTOR;
    }

    @Override
    public void enable(FeatureContext context) {
        active = Objects.requireNonNull(context, "context");
    }

    @Override
    public void disable() {
        active = null;
    }

    /** The current settings, or null while the feature is inactive. Rebuilt only when the snapshot changes. */
    static MouseTweaksConfig config() {
        FeatureContext context = active;
        if (context == null) {
            return null;
        }
        SettingsSnapshot settings = context.settings();
        if (settings != cachedFrom) {
            cachedFrom = settings;
            cached = MouseTweaksConfig.from(settings);
        }
        return cached;
    }
}
