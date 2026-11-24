package io.github.brener_fregulia.qolunified.ui;

import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import java.util.Locale;

/** Translation keys used by the settings UI. Every constant must exist in en_us and pt_br. */
public final class UiKeys {
    public static final String KEY_OPEN_SETTINGS = "key.qol_unified.open_settings";
    public static final String KEY_CATEGORY = "key.category.qol_unified.main";

    public static final String TITLE = "qol_unified.settings.title";
    public static final String SEARCH_HINT = "qol_unified.settings.search";
    public static final String EMPTY_REGISTRY = "qol_unified.settings.empty";
    public static final String NO_MATCHES = "qol_unified.settings.no_matches";
    public static final String DONE = "qol_unified.settings.done";
    public static final String CANCEL = "qol_unified.settings.cancel";
    public static final String BACK = "qol_unified.settings.back";
    public static final String FEATURE_SETTINGS = "qol_unified.settings.feature_settings";
    public static final String NO_SETTINGS = "qol_unified.settings.no_settings";
    public static final String RETRY_DISABLE = "qol_unified.settings.retry_disable";
    public static final String ENABLED = "qol_unified.settings.enabled";
    public static final String DISABLED = "qol_unified.settings.disabled";
    public static final String RESTART_REQUIRED_SUFFIX = "qol_unified.settings.restart_required_suffix";
    public static final String MAP_ENTRIES = "qol_unified.settings.map_entries";

    public static final String STATUS_ACTIVE = "qol_unified.status.active";
    public static final String STATUS_INACTIVE = "qol_unified.status.inactive";
    public static final String STATUS_UNKNOWN = "qol_unified.status.unknown";
    public static final String TOOLTIP_UNKNOWN = "qol_unified.tooltip.unknown";
    public static final String STATUS_ENABLE_FAILED = "qol_unified.status.enable_failed";
    public static final String STATUS_PENDING_RESTART = "qol_unified.status.pending_restart";
    public static final String STATUS_CONFLICT_DEFAULT = "qol_unified.status.conflict_default";
    public static final String STATUS_CONFLICT_OVERRIDE = "qol_unified.status.conflict_override";
    public static final String STATUS_CONFLICT = "qol_unified.status.conflict";

    public static final String MESSAGE_READ_ONLY = "qol_unified.message.read_only";
    public static final String MESSAGE_INVALID = "qol_unified.message.invalid";
    public static final String MESSAGE_SAVE_FAILED = "qol_unified.message.save_failed";
    public static final String MESSAGE_APPLY_FAILED = "qol_unified.message.apply_failed";
    public static final String MESSAGE_RESTART = "qol_unified.message.restart";
    public static final String MESSAGE_RETRY_FAILED = "qol_unified.message.retry_failed";
    public static final String MESSAGE_RETRY_SUCCEEDED = "qol_unified.message.retry_succeeded";

    public static final String ERROR_NOT_A_NUMBER = "qol_unified.error.not_a_number";
    public static final String ERROR_OUT_OF_RANGE = "qol_unified.error.out_of_range";
    public static final String ERROR_INVALID_VALUE = "qol_unified.error.invalid_value";

    private UiKeys() {
    }

    public static String category(FeatureCategory category) {
        return "qol_unified.category." + category.name().toLowerCase(Locale.ROOT);
    }

    /** Key for an enum setting value: {@code qol_unified.feature.<id>.setting.<key>.<value>}. */
    public static String enumValue(String featureId, String settingKey, String value) {
        return "qol_unified.feature." + featureId + ".setting." + settingKey + "." + value;
    }
}
