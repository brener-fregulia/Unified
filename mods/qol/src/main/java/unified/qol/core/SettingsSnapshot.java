package unified.qol.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, validated setting values of one feature (M0 §3.2, §4.5). Every declared setting has a
 * value; missing values use the declared default.
 */
public final class SettingsSnapshot {
    private final FeatureDescriptor descriptor;
    private final Map<String, Object> values;

    private SettingsSnapshot(FeatureDescriptor descriptor, Map<String, Object> values) {
        this.descriptor = descriptor;
        this.values = values;
    }

    public static SettingsSnapshot defaults(FeatureDescriptor descriptor) {
        return of(descriptor, Map.of());
    }

    /**
     * Builds a snapshot from explicit values.
     *
     * @throws IllegalArgumentException if a key is unknown or a value is invalid
     */
    public static SettingsSnapshot of(FeatureDescriptor descriptor, Map<String, ?> overrides) {
        Objects.requireNonNull(descriptor, "descriptor");
        for (var entry : overrides.entrySet()) {
            SettingDescriptor setting = descriptor.setting(entry.getKey()).orElseThrow(() ->
                    new IllegalArgumentException("Feature " + descriptor.id() + ": unknown setting " + entry.getKey()));
            if (!setting.accepts(entry.getValue())) {
                throw new IllegalArgumentException("Feature " + descriptor.id() + ": invalid value for "
                        + entry.getKey() + ": " + entry.getValue());
            }
        }
        var values = new LinkedHashMap<String, Object>();
        for (SettingDescriptor setting : descriptor.settings()) {
            values.put(setting.key(), overrides.containsKey(setting.key())
                    ? overrides.get(setting.key())
                    : setting.defaultValue());
        }
        return new SettingsSnapshot(descriptor, java.util.Collections.unmodifiableMap(values));
    }

    /** Returns a copy with one value replaced. */
    public SettingsSnapshot with(String key, Object value) {
        var copy = new LinkedHashMap<String, Object>(values);
        copy.put(key, value);
        return of(descriptor, copy);
    }

    public FeatureDescriptor descriptor() {
        return descriptor;
    }

    public Map<String, Object> values() {
        return values;
    }

    public Object get(String key) {
        if (!values.containsKey(key)) {
            throw new IllegalArgumentException("Feature " + descriptor.id() + ": unknown setting " + key);
        }
        return values.get(key);
    }

    public boolean getBoolean(String key) {
        return (Boolean) get(key);
    }

    public int getInt(String key) {
        return (Integer) get(key);
    }

    public String getEnum(String key) {
        return (String) get(key);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SettingsSnapshot other
                && descriptor.id().equals(other.descriptor.id())
                && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor.id(), values);
    }

    @Override
    public String toString() {
        return "SettingsSnapshot[" + descriptor.id() + "=" + values + "]";
    }
}
