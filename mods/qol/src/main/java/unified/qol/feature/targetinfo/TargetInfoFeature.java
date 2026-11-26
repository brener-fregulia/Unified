package unified.qol.feature.targetinfo;

import unified.qol.core.Feature;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingsSnapshot;
import java.util.Objects;

/**
 * Target Info (target-info-v1.md). The HUD element is registered once, on the first enable, and draws nothing while
 * the feature is inactive, so enabling and disabling is LIVE and disabling cannot fail.
 */
public final class TargetInfoFeature implements Feature {
    private static volatile FeatureContext active;

    private final Runnable installHooks;
    private boolean hooksInstalled;

    public TargetInfoFeature() {
        // Resolved only on the first enable, so loading the registry does not load rendering classes.
        this(() -> TargetInfoHooks.install());
    }

    TargetInfoFeature(Runnable installHooks) {
        this.installHooks = Objects.requireNonNull(installHooks, "installHooks");
    }

    @Override
    public FeatureDescriptor descriptor() {
        return TargetInfoSettings.DESCRIPTOR;
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
