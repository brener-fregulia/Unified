package io.github.brener_fregulia.qolunified.core;

/**
 * A feature implementation (M0 §3.4). Lifecycle calls are made by {@link FeatureLifecycle} only.
 *
 * <p>{@link #disable()} must be safe to call after a partial or failed {@link #enable(FeatureContext)}
 * and must only undo what is actually installed.
 */
public interface Feature {
    FeatureDescriptor descriptor();

    void enable(FeatureContext context);

    void disable();
}
