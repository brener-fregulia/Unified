package unified.qol.core;

import java.util.regex.Pattern;

/** Identifier rules from the M0 specification (§3.1, §3.2). */
final class Identifiers {
    static final String TRANSLATION_PREFIX = "qol_unified.feature.";
    private static final Pattern FEATURE_ID = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Pattern SETTING_KEY = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    private Identifiers() {
    }

    static String requireFeatureId(String id) {
        if (id == null || !FEATURE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid feature id: " + id);
        }
        return id;
    }

    static String requireSettingKey(String key) {
        if (key == null || !SETTING_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid setting key: " + key);
        }
        return key;
    }
}
