package unified.qol.feature.keyboardmap;

import unified.qol.core.Feature;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingsSnapshot;
import java.util.Objects;

/**
 * Keyboard map (keymap-v1.md). Its entry points are installed once and check {@link #activeSettings()}, so while the
 * feature is disabled none of them opens the screen; enabling and disabling is LIVE and cannot fail. The "Open
 * Keymap" key mapping itself is registered at client start (Fabric only accepts key mappings then), unbound.
 */
public final class KeyboardMapFeature implements Feature {
    private static volatile FeatureContext active;

    private final Runnable installHooks;
    private boolean hooksInstalled;

    public KeyboardMapFeature() {
        // Resolved only on the first enable, so loading the registry does not load screen classes.
        this(() -> KeyboardMapHooks.installEntryPoints());
    }

    KeyboardMapFeature(Runnable installHooks) {
        this.installHooks = Objects.requireNonNull(installHooks, "installHooks");
    }

    @Override
    public FeatureDescriptor descriptor() {
        return KeyboardMapSettings.DESCRIPTOR;
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
