package io.github.brener_fregulia.qolunified;

import io.github.brener_fregulia.qolunified.core.Feature;
import java.util.List;

/**
 * Development-only source of sample features, provided through the {@code qol_unified:dev_features}
 * entrypoint. It is consulted only when Fabric reports a development environment, so it has no effect
 * in a normal install.
 */
public interface DevelopmentFeatures {
    String ENTRYPOINT = "qol_unified:dev_features";

    List<Feature> features();
}
