package io.github.brener_fregulia.qolunified.core;

import java.util.Objects;

/** What the player has chosen for one feature: enabled state and setting values. */
public record DesiredFeature(boolean enabled, SettingsSnapshot settings) {
    public DesiredFeature {
        Objects.requireNonNull(settings, "settings");
    }

    public static DesiredFeature defaults(FeatureDescriptor descriptor) {
        return new DesiredFeature(descriptor.defaultEnabled(), SettingsSnapshot.defaults(descriptor));
    }
}
