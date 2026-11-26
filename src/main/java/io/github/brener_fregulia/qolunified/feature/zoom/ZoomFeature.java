package io.github.brener_fregulia.qolunified.feature.zoom;

import io.github.brener_fregulia.qolunified.core.Feature;
import io.github.brener_fregulia.qolunified.core.FeatureContext;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import java.util.Objects;

/**
 * Zoom (zoom-v1.md). The key mapping and Mixins exist from startup (Fabric accepts key mappings only then); they act
 * only while this feature is active, so enabling and disabling is LIVE and cannot fail.
 */
public final class ZoomFeature implements Feature {
    private static volatile FeatureContext active;

    @Override
    public FeatureDescriptor descriptor() {
        return ZoomSettings.DESCRIPTOR;
    }

    @Override
    public void enable(FeatureContext context) {
        active = Objects.requireNonNull(context, "context");
    }

    @Override
    public void disable() {
        active = null;
        ZoomHooks.featureDisabled();
    }

    /** The running feature's context, or null while inactive. */
    static FeatureContext active() {
        return active;
    }
}
