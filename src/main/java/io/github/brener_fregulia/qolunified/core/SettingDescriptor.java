package io.github.brener_fregulia.qolunified.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

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

    /**
     * A bounded map from validated string keys to small records of scalar fields (sound-control-v1.md §8.2). The value
     * is a {@code Map<String, Map<String, Object>>} whose entries hold every field with an accepted value; the default
     * is the empty map. The fields' own apply modes are unused: this setting's apply mode applies.
     */
    record MapSetting(String key, Pattern keyPattern, int maxEntries, List<SettingDescriptor> fields,
            ApplyMode applyMode) implements SettingDescriptor {
        public MapSetting {
            Identifiers.requireSettingKey(key);
            Objects.requireNonNull(keyPattern, "keyPattern");
            Objects.requireNonNull(applyMode, "applyMode");
            if (maxEntries < 1) {
                throw new IllegalArgumentException("Setting " + key + ": maxEntries must be positive");
            }
            fields = List.copyOf(fields);
            if (fields.isEmpty() || fields.stream().map(SettingDescriptor::key).distinct().count() != fields.size()) {
                throw new IllegalArgumentException("Setting " + key + ": fields must be non-empty and unique");
            }
            if (fields.stream().anyMatch(f -> f instanceof MapSetting)) {
                throw new IllegalArgumentException("Setting " + key + ": map fields must be scalar");
            }
        }

        @Override
        public Map<String, Map<String, Object>> defaultValue() {
            return Map.of();
        }

        public boolean acceptsKey(String entryKey) {
            return entryKey != null && keyPattern.matcher(entryKey).matches();
        }

        /** Whether {@code entry} holds exactly the declared fields, each with an accepted value. */
        public boolean acceptsEntry(Object entry) {
            if (!(entry instanceof Map<?, ?> map) || map.size() != fields.size()) {
                return false;
            }
            for (SettingDescriptor field : fields) {
                if (!field.accepts(map.get(field.key()))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean accepts(Object value) {
            if (!(value instanceof Map<?, ?> map) || map.size() > maxEntries) {
                return false;
            }
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String k) || !acceptsKey(k) || !acceptsEntry(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }
}
