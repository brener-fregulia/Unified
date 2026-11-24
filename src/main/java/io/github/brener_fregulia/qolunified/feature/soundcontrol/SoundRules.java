package io.github.brener_fregulia.qolunified.feature.soundcontrol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Conversions between the stored map setting, editable rules and the lookup table used on the audio path. */
public final class SoundRules {
    private SoundRules() {
    }

    /** Rules from the setting value; entries equal to the defaults are dropped (they change nothing). */
    @SuppressWarnings("unchecked")
    public static Map<String, SoundRule> read(Object settingValue) {
        var rules = new TreeMap<String, SoundRule>();
        ((Map<String, Map<String, Object>>) settingValue).forEach((id, entry) -> {
            SoundRule rule = SoundRule.fromEntry(entry);
            if (!rule.isDefault()) {
                rules.put(id, rule);
            }
        });
        return Collections.unmodifiableMap(rules);
    }

    /** The setting value for {@code rules}; default rules are not written. */
    public static Map<String, Map<String, Object>> write(Map<String, SoundRule> rules) {
        var value = new TreeMap<String, Map<String, Object>>();
        rules.forEach((id, rule) -> {
            if (!rule.isDefault()) {
                value.put(id, rule.toEntry());
            }
        });
        return Collections.unmodifiableMap(value);
    }

    /**
     * An immutable factor table keyed by {@code parse(id)}; ids that do not parse are skipped. The result accepts
     * {@code get(null)}, so a mod's instance without an id cannot throw on the audio path.
     */
    public static <K> Map<K, Float> table(Map<String, SoundRule> rules, Function<String, K> parse) {
        if (rules.isEmpty()) {
            return Map.of();
        }
        var table = new HashMap<K, Float>(rules.size() * 2);
        rules.forEach((id, rule) -> {
            K key = parse.apply(id);
            if (key != null) {
                table.put(key, rule.factor());
            }
        });
        return Collections.unmodifiableMap(table);
    }

    /** The factor for {@code key}, 1 when no rule exists. */
    public static <K> float factor(Map<K, Float> table, K key) {
        if (table.isEmpty()) {
            return 1f;
        }
        Float factor = table.get(key);
        return factor == null ? 1f : factor;
    }
}
