package unified.qol.feature.foodhud;

import unified.qol.core.Feature;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingsSnapshot;
import java.util.Objects;

/**
 * Food HUD (food-hud-v1.md). The rendering hooks (Mixins, tooltip and debug registrations) are installed once and
 * stay; they only act while this feature is active, so enabling and disabling is LIVE and disabling cannot fail.
 */
public final class FoodHudFeature implements Feature {
    private static volatile FeatureContext active;

    private final Runnable installHooks;
    private boolean hooksInstalled;

    public FoodHudFeature() {
        // Resolved only on the first enable, so loading the registry does not load rendering classes.
        this(() -> FoodHudHooks.install());
    }

    FoodHudFeature(Runnable installHooks) {
        this.installHooks = Objects.requireNonNull(installHooks, "installHooks");
    }

    @Override
    public FeatureDescriptor descriptor() {
        return FoodHudSettings.DESCRIPTOR;
    }

    @Override
    public void enable(FeatureContext context) {
        Objects.requireNonNull(context, "context");
        if (!hooksInstalled) {
            installHooks.run();
            hooksInstalled = true;
        }
        active = context;
    }

    @Override
    public void disable() {
        active = null;
    }

    /** The current effective settings, or {@code null} while the feature is inactive. */
    public static SettingsSnapshot activeSettings() {
        FeatureContext context = active;
        return context == null ? null : context.settings();
    }
}
