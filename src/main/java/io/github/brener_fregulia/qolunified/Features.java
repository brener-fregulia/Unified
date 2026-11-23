package io.github.brener_fregulia.qolunified;

import io.github.brener_fregulia.qolunified.core.Feature;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import io.github.brener_fregulia.qolunified.feature.chatheads.ChatHeadsFeature;
import io.github.brener_fregulia.qolunified.feature.foodhud.FoodHudFeature;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyboardMapFeature;
import io.github.brener_fregulia.qolunified.feature.targetinfo.TargetInfoFeature;
import java.util.List;

/** The production feature list. Features are registered here explicitly (M0 §3.5). */
public final class Features {
    private Features() {
    }

    public static FeatureRegistry createRegistry() {
        return createRegistry(List.of());
    }

    /** Production features followed by development-only sample features (empty outside development). */
    public static FeatureRegistry createRegistry(List<Feature> developmentFeatures) {
        FeatureRegistry.Builder builder = FeatureRegistry.builder();
        builder.register(new FoodHudFeature());
        builder.register(new ChatHeadsFeature());
        builder.register(new KeyboardMapFeature());
        builder.register(new TargetInfoFeature());
        developmentFeatures.forEach(builder::register);
        return builder.build();
    }
}
