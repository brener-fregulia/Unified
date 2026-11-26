package unified.qol.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable description of a feature (M0 §3.1). Invalid descriptors are rejected on construction. */
public record FeatureDescriptor(
        String id,
        FeatureCategory category,
        boolean defaultEnabled,
        ApplyMode toggleMode,
        List<SettingDescriptor> settings,
        List<ConflictDescriptor> conflicts,
        SettingsRule rule) {

    /** A descriptor whose settings have no cross-setting rule. */
    public FeatureDescriptor(String id, FeatureCategory category, boolean defaultEnabled, ApplyMode toggleMode,
            List<SettingDescriptor> settings, List<ConflictDescriptor> conflicts) {
        this(id, category, defaultEnabled, toggleMode, settings, conflicts, SettingsRule.NONE);
    }

    public FeatureDescriptor {
        Objects.requireNonNull(rule, "rule");
        Identifiers.requireFeatureId(id);
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(toggleMode, "toggleMode");
        settings = List.copyOf(settings);
        conflicts = List.copyOf(conflicts);
        var keys = new HashSet<String>();
        for (SettingDescriptor setting : settings) {
            if (!keys.add(setting.key())) {
                throw new IllegalArgumentException("Feature " + id + ": duplicate setting key " + setting.key());
            }
        }
    }

    public String nameKey() {
        return Identifiers.TRANSLATION_PREFIX + id + ".name";
    }

    public String descriptionKey() {
        return Identifiers.TRANSLATION_PREFIX + id + ".description";
    }

    public Optional<SettingDescriptor> setting(String key) {
        return settings.stream().filter(s -> s.key().equals(key)).findFirst();
    }
}
