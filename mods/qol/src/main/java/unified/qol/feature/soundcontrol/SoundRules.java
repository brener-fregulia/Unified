package unified.qol.feature.soundcontrol;

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

    /**
     * The preview override for one event (sound-control-v1.md §16): the factor of {@code target}, or null when it
     * equals the committed factor, so unchanged events fall through to the committed table.
     */
    public static Float override(SoundRule target, SoundRule committed) {
        float wanted = (target == null ? SoundRule.DEFAULT : target).factor();
        float current = (committed == null ? SoundRule.DEFAULT : committed).factor();
        return wanted == current ? null : wanted;
    }

    /** {@code factors} keyed by {@code parse(id)}, skipping ids that do not parse; empty input gives {@code Map.of()}. */
    public static <K> Map<K, Float> keyed(Map<String, Float> factors, Function<String, K> parse) {
        if (factors.isEmpty()) {
            return Map.of();
        }
        var table = new HashMap<K, Float>(factors.size() * 2);
        factors.forEach((id, factor) -> {
            K key = parse.apply(id);
            if (key != null) {
                table.put(key, factor);
            }
        });
        return Collections.unmodifiableMap(table);
    }

    /** Every override between {@code target} and {@code committed}, keyed by event id. */
    public static Map<String, Float> overrides(Map<String, SoundRule> target, Map<String, SoundRule> committed) {
        var result = new TreeMap<String, Float>();
        var ids = new java.util.TreeSet<String>(target.keySet());
        ids.addAll(committed.keySet());
        for (String id : ids) {
            Float factor = override(target.get(id), committed.get(id));
            if (factor != null) {
                result.put(id, factor);
            }
        }
        return Collections.unmodifiableMap(result);
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
