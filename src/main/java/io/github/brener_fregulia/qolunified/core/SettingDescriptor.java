package io.github.brener_fregulia.qolunified.core;

import java.util.List;
import java.util.Objects;

/** A typed, validated feature setting (M0 §3.2). */
public sealed interface SettingDescriptor {
    String key();

    Object defaultValue();

    ApplyMode applyMode();

    /** Returns whether {@code value} is valid for this setting. */
    boolean accepts(Object value);

    default String nameKey(String featureId) {
        return Identifiers.TRANSLATION_PREFIX + featureId + ".setting." + key();
    }

    record BooleanSetting(String key, Boolean defaultValue, ApplyMode applyMode) implements SettingDescriptor {
        public BooleanSetting {
            Identifiers.requireSettingKey(key);
            Objects.requireNonNull(defaultValue, "defaultValue");
            Objects.requireNonNull(applyMode, "applyMode");
        }

        @Override
        public boolean accepts(Object value) {
            return value instanceof Boolean;
        }
    }

    record IntegerSetting(String key, Integer defaultValue, int min, int max, ApplyMode applyMode)
            implements SettingDescriptor {
        public IntegerSetting {
            Identifiers.requireSettingKey(key);
            Objects.requireNonNull(defaultValue, "defaultValue");
            Objects.requireNonNull(applyMode, "applyMode");
            if (min > max) {
                throw new IllegalArgumentException("Setting " + key + ": min > max");
            }
            if (defaultValue < min || defaultValue > max) {
                throw new IllegalArgumentException("Setting " + key + ": default out of range");
            }
        }

        @Override
        public boolean accepts(Object value) {
            return value instanceof Integer i && i >= min && i <= max;
        }
    }

    record EnumSetting(String key, String defaultValue, List<String> values, ApplyMode applyMode)
            implements SettingDescriptor {
        public EnumSetting {
            Identifiers.requireSettingKey(key);
            Objects.requireNonNull(applyMode, "applyMode");
            values = List.copyOf(values);
            if (values.isEmpty() || values.stream().distinct().count() != values.size()) {
                throw new IllegalArgumentException("Setting " + key + ": values must be non-empty and unique");
            }
            if (!values.contains(defaultValue)) {
                throw new IllegalArgumentException("Setting " + key + ": default is not one of the values");
            }
        }

        @Override
        public boolean accepts(Object value) {
            return value instanceof String s && values.contains(s);
        }
    }
}
