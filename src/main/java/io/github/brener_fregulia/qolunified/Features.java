package io.github.brener_fregulia.qolunified;

import io.github.brener_fregulia.qolunified.core.FeatureRegistry;

/** The production feature list. Features are registered here explicitly (M0 §3.5). */
public final class Features {
    private Features() {
    }

    public static FeatureRegistry createRegistry() {
        // No features exist yet; Food HUD is the first, in M1.
        return FeatureRegistry.builder().build();
    }
}
