package unified.qol.feature.shulkerpreview;

import unified.qol.core.Feature;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingsSnapshot;
import java.util.Objects;

/**
 * Shulker Preview (shulker-preview-v1.md). The Mixins are always applied and the tooltip callback is registered once,
 * on the first enable; all of them act only while this feature is active, so enabling and disabling is LIVE.
 */
public final class ShulkerPreviewFeature implements Feature {
    private static volatile FeatureContext active;

    private final Runnable installHooks;
    private boolean hooksInstalled;

    public ShulkerPreviewFeature() {
        // Resolved only on the first enable, so loading the registry does not load rendering classes.
        this(() -> ShulkerPreviewHooks.install());
    }

    ShulkerPreviewFeature(Runnable installHooks) {
        this.installHooks = Objects.requireNonNull(installHooks, "installHooks");
    }

    @Override
    public FeatureDescriptor descriptor() {
        return ShulkerPreviewSettings.DESCRIPTOR;
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
