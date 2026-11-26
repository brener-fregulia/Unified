package unified.qol.core.config;

import unified.qol.core.DesiredFeature;
import unified.qol.core.SettingsSnapshot;
import java.util.Objects;

/**
 * The configured state of one feature. {@code explicitEnabled} is true when the player has made an
 * explicit enable choice (CFG-5); otherwise {@code enabled} is the default.
 */
public record FeatureChoice(boolean enabled, boolean explicitEnabled, SettingsSnapshot settings) {
    public FeatureChoice {
        Objects.requireNonNull(settings, "settings");
    }

    public DesiredFeature toDesired() {
        return new DesiredFeature(enabled, settings);
    }

    /** A copy carrying an explicit player choice. */
    public FeatureChoice withEnabled(boolean value) {
        return new FeatureChoice(value, true, settings);
    }

    public FeatureChoice withSettings(SettingsSnapshot value) {
        return new FeatureChoice(enabled, explicitEnabled, value);
    }
}
