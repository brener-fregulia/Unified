package io.github.brener_fregulia.qolunified.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.brener_fregulia.qolunified.core.Feature;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads, validates, migrates and saves the global configuration file (CFG-P1–P8, CFG-1–CFG-6).
 *
 * <p>The parsed JSON document is kept in memory, so unknown features, settings and fields survive a
 * save. A save writes only explicit enable choices and setting values the player changed. Invalid
 * original values stay in the file until the player changes that setting. Not thread-safe; use from
 * the client thread.
 */
public final class ConfigurationService {
    static final String SCHEMA_VERSION = "schemaVersion";
    static final String FEATURES = "features";
    static final String ENABLED = "enabled";
    static final String SETTINGS = "settings";

    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();
    private static final DateTimeFormatter BACKUP_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final Path file;
    private final FeatureRegistry registry;
    private final Migrations migrations;
    private final ConfigFileSystem fs;
    private final Clock clock;

    private JsonObject root;
    private boolean diskUpToDate;
    private LoadStatus status;
    private Map<String, FeatureChoice> choices = Map.of();

    public ConfigurationService(Path file, FeatureRegistry registry) {
        this(file, registry, Migrations.production(), new ConfigFileSystem(), Clock.systemUTC());
    }

    public ConfigurationService(Path file, FeatureRegistry registry, Migrations migrations, ConfigFileSystem fs,
            Clock clock) {
        this.file = Objects.requireNonNull(file, "file");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.migrations = Objects.requireNonNull(migrations, "migrations");
        this.fs = Objects.requireNonNull(fs, "fs");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Loads the file. Never throws for file problems; the outcome is returned and logged. */
    public LoadStatus load() {
        if (!fs.exists(file)) {
            return useDefaults(LoadStatus.MISSING);
        }
        byte[] bytes;
        try {
            bytes = fs.read(file);
        } catch (IOException e) {
            LOGGER.error("Could not read configuration {} ({}); defaults are in use and changes will not be saved",
                    name(), e.getClass().getSimpleName());
            return useDefaults(LoadStatus.READ_FAILED);
        }
        JsonObject parsed = parse(bytes);
        Integer version = parsed == null ? null : schemaVersion(parsed);
        if (parsed == null || version == null) {
            return recoverCorrupt(parsed == null ? "not a valid JSON object" : "missing or invalid schemaVersion");
        }
        if (parsed.has(FEATURES) && !parsed.get(FEATURES).isJsonObject()) {
            return recoverCorrupt("'features' is not an object");
        }
        if (version > migrations.currentVersion()) {
            LOGGER.warn("Configuration {} has newer schema version {} (supported: {}); it will not be modified",
                    name(), version, migrations.currentVersion());
            root = parsed;
            diskUpToDate = true;
            return finish(LoadStatus.NEWER_VERSION, interpret(parsed));
        }
        if (version < migrations.currentVersion()) {
            if (!backup("pre-migration")) {
                LOGGER.error("Configuration {} needs migration but could not be backed up; it will not be modified",
                        name());
                return useDefaults(LoadStatus.RECOVERY_BLOCKED);
            }
            try {
                root = migrations.migrate(parsed, version);
            } catch (Migrations.MigrationException e) {
                LOGGER.error("Configuration {} could not be migrated from version {}: {}; defaults are in use and the "
                        + "file will not be modified", name(), version, e.getMessage(), e.getCause());
                return useDefaults(LoadStatus.MIGRATION_FAILED);
            }
            if (root.has(FEATURES) && !root.get(FEATURES).isJsonObject()) {
                LOGGER.error("Configuration {} migration produced an invalid document; it will not be modified", name());
                return useDefaults(LoadStatus.MIGRATION_FAILED);
            }
            diskUpToDate = false;
            LOGGER.info("Configuration {} migrated from schema version {} to {}; it will be written on next save",
                    name(), version, migrations.currentVersion());
            return finish(LoadStatus.MIGRATED, interpret(root));
        }
        root = parsed;
        diskUpToDate = true;
        return finish(LoadStatus.LOADED, interpret(parsed));
    }

    /**
     * Saves a complete or partial draft of feature choices (TX-5). Writes only changes; unchanged
     * entries keep their original JSON, including invalid values.
     *
     * @throws IllegalArgumentException if the draft names an unknown feature or uses foreign settings
     */
    public SaveResult save(Map<String, FeatureChoice> draft) {
        requireLoaded();
        for (var entry : draft.entrySet()) {
            Feature feature = registry.require(entry.getKey());
            if (entry.getValue().settings().descriptor() != feature.descriptor()) {
                throw new IllegalArgumentException("Settings for " + entry.getKey() + " use a different descriptor");
            }
        }
        if (status.readOnly()) {
            LOGGER.warn("Configuration {} is read-only ({}); changes were not saved", name(), status);
            return SaveResult.READ_ONLY;
        }
        JsonObject next = root.deepCopy();
        next.addProperty(SCHEMA_VERSION, migrations.currentVersion());
        for (Feature feature : registry.features()) {
            String id = feature.descriptor().id();
            FeatureChoice wanted = draft.get(id);
            if (wanted != null) {
                writeChanges(next, feature.descriptor(), choices.get(id), wanted);
            }
        }
        if (diskUpToDate && next.equals(root)) {
            return SaveResult.NO_CHANGE;
        }
        if (!write(next)) {
            return SaveResult.FAILED;
        }
        root = next;
        diskUpToDate = true;
        var updated = new LinkedHashMap<>(choices);
        for (var entry : draft.entrySet()) {
            FeatureChoice old = choices.get(entry.getKey());
            FeatureChoice wanted = entry.getValue();
            updated.put(entry.getKey(), new FeatureChoice(
                    wanted.explicitEnabled() ? wanted.enabled() : old.enabled(),
                    wanted.explicitEnabled() || old.explicitEnabled(),
                    wanted.settings()));
        }
        choices = Collections.unmodifiableMap(updated);
        return SaveResult.SAVED;
    }

    public LoadStatus status() {
        requireLoaded();
        return status;
    }

    /** True when changes cannot be saved this session (TX-9). */
    public boolean readOnly() {
        return status().readOnly();
    }

    /** Current configured choice for every registered feature, in registry order. */
    public Map<String, FeatureChoice> choices() {
        requireLoaded();
        return choices;
    }

    private void writeChanges(JsonObject next, FeatureDescriptor descriptor, FeatureChoice old, FeatureChoice wanted) {
        boolean enabledChanged = wanted.explicitEnabled()
                && (!old.explicitEnabled() || old.enabled() != wanted.enabled());
        var changedSettings = new LinkedHashMap<String, Object>();
        for (SettingDescriptor setting : descriptor.settings()) {
            Object value = wanted.settings().get(setting.key());
            if (!value.equals(old.settings().get(setting.key()))) {
                changedSettings.put(setting.key(), value);
            }
        }
        if (!enabledChanged && changedSettings.isEmpty()) {
            return;
        }
        JsonObject features = childObject(next, FEATURES);
        JsonObject feature = childObject(features, descriptor.id());
        if (enabledChanged) {
            feature.addProperty(ENABLED, wanted.enabled());
        }
        if (!changedSettings.isEmpty()) {
            JsonObject settings = childObject(feature, SETTINGS);
            changedSettings.forEach((key, value) -> settings.add(key,
                    descriptor.setting(key).orElseThrow() instanceof SettingDescriptor.MapSetting map
                            ? mergeMap(map, settings.get(key), old.settings().get(key), value)
                            : toJson(value)));
        }
    }

    /** Returns the object child, replacing a non-object value only because the player changed it. */
    private static JsonObject childObject(JsonObject parent, String key) {
        JsonElement child = parent.get(key);
        if (child != null && child.isJsonObject()) {
            return child.getAsJsonObject();
        }
        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }

    private boolean write(JsonObject document) {
        byte[] content = (GSON.toJson(document) + "\n").getBytes(StandardCharsets.UTF_8);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (file.getParent() != null) {
                fs.createDirectories(file.getParent());
            }
            fs.write(temp, content);
            try {
                fs.moveAtomic(temp, file);
            } catch (AtomicMoveNotSupportedException e) {
                LOGGER.warn("Atomic file replacement is not supported here; replacing {} non-atomically", name());
                fs.moveReplacing(temp, file);
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Could not save configuration {} ({}); the previous file is unchanged",
                    name(), e.getClass().getSimpleName());
            try {
                fs.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Best effort; a leftover temporary file does not affect the configuration.
            }
            return false;
        }
    }

    private LoadStatus recoverCorrupt(String reason) {
        if (backup("corrupt")) {
            LOGGER.warn("Configuration {} is unreadable ({}); a backup was saved and defaults are in use. "
                    + "The file will be replaced on next save", name(), reason);
            return useDefaults(LoadStatus.RECOVERED);
        }
        LOGGER.error("Configuration {} is unreadable ({}) and could not be backed up; defaults are in use and the "
                + "file will not be modified", name(), reason);
        return useDefaults(LoadStatus.RECOVERY_BLOCKED);
    }

    private boolean backup(String reason) {
        Path dir = file.resolveSibling("backups");
        String stem = "config-" + BACKUP_TIME.format(clock.instant()) + "-" + reason;
        try {
            fs.createDirectories(dir);
            for (int i = 1; i <= 100; i++) {
                Path target = dir.resolve(i == 1 ? stem + ".json" : stem + "-" + i + ".json");
                if (!fs.exists(target)) {
                    fs.copy(file, target);
                    LOGGER.info("Configuration backup saved as backups/{}", target.getFileName());
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            LOGGER.error("Could not back up configuration {} ({})", name(), e.getClass().getSimpleName());
            return false;
        }
    }

    private LoadStatus useDefaults(LoadStatus outcome) {
        root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, migrations.currentVersion());
        diskUpToDate = false;
        var defaults = new LinkedHashMap<String, FeatureChoice>();
        for (Feature feature : registry.features()) {
            FeatureDescriptor d = feature.descriptor();
            defaults.put(d.id(), new FeatureChoice(d.defaultEnabled(), false, SettingsSnapshot.defaults(d)));
        }
        return finish(outcome, defaults);
    }

    private LoadStatus finish(LoadStatus outcome, Map<String, FeatureChoice> loaded) {
        status = outcome;
        choices = Collections.unmodifiableMap(loaded);
        return outcome;
    }

    private Map<String, FeatureChoice> interpret(JsonObject document) {
        JsonObject features = document.has(FEATURES) ? document.getAsJsonObject(FEATURES) : new JsonObject();
        var result = new LinkedHashMap<String, FeatureChoice>();
        for (Feature feature : registry.features()) {
            FeatureDescriptor d = feature.descriptor();
            JsonElement element = features.get(d.id());
            JsonObject entry = new JsonObject();
            if (element != null && element.isJsonObject()) {
                entry = element.getAsJsonObject();
            } else if (element != null) {
                LOGGER.warn("Configuration: entry for feature {} is not an object; defaults are in use", d.id());
            }
            boolean enabled = d.defaultEnabled();
            boolean explicit = false;
            JsonElement enabledElement = entry.get(ENABLED);
            if (enabledElement != null) {
                if (enabledElement instanceof JsonPrimitive p && p.isBoolean()) {
                    enabled = p.getAsBoolean();
                    explicit = true;
                } else {
                    LOGGER.warn("Configuration: feature {} has an invalid 'enabled' value; the default is in use", d.id());
                }
            }
            JsonObject settingsJson = new JsonObject();
            JsonElement settingsElement = entry.get(SETTINGS);
            if (settingsElement != null && settingsElement.isJsonObject()) {
                settingsJson = settingsElement.getAsJsonObject();
            } else if (settingsElement != null) {
                LOGGER.warn("Configuration: settings of feature {} are not an object; defaults are in use", d.id());
            }
            var values = new LinkedHashMap<String, Object>();
            for (SettingDescriptor setting : d.settings()) {
                JsonElement raw = settingsJson.get(setting.key());
                if (raw == null) {
                    continue;
                }
                Object value = setting instanceof SettingDescriptor.MapSetting map
                        ? mapFromJson(d.id(), map, raw)
                        : fromJson(setting, raw);
                if (value != null && setting.accepts(value)) {
                    values.put(setting.key(), value);
                } else {
                    LOGGER.warn("Configuration: setting {}.{} has an invalid value; the default is in use",
                            d.id(), setting.key());
                }
            }
            SettingsSnapshot snapshot = SettingsSnapshot.of(d, values);
            if (d.rule().check(snapshot, null).isPresent()) {
                // A hand-edited file can combine individually valid values into a contradiction (e.g. a minimum
                // above a maximum); the feature's settings fall back to their defaults rather than applying it.
                LOGGER.warn("Configuration: settings of feature {} contradict each other; defaults are in use",
                        d.id());
                snapshot = SettingsSnapshot.defaults(d);
            }
            result.put(d.id(), new FeatureChoice(enabled, explicit, snapshot));
        }
        return result;
    }

    /**
     * Reads a map setting entry by entry (sound-control-v1.md §8.2). Invalid entries and entries beyond the limit are
     * skipped and counted in one warning; they stay in the file. Returns null if the value is not an object.
     */
    private static Map<String, Map<String, Object>> mapFromJson(String featureId, SettingDescriptor.MapSetting map,
            JsonElement raw) {
        if (!raw.isJsonObject()) {
            return null;
        }
        var result = new TreeMap<String, Map<String, Object>>();
        int skipped = 0;
        for (var member : raw.getAsJsonObject().entrySet()) {
            Map<String, Object> entry = result.size() < map.maxEntries() && map.acceptsKey(member.getKey())
                    ? entryFromJson(map, member.getValue()) : null;
            if (entry == null) {
                skipped++;
            } else {
                result.put(member.getKey(), entry);
            }
        }
        if (skipped > 0) {
            LOGGER.warn("Configuration: setting {}.{} has {} invalid or excess entries; they are ignored and kept "
                    + "in the file", featureId, map.key(), skipped);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> entryFromJson(SettingDescriptor.MapSetting map, JsonElement raw) {
        if (!raw.isJsonObject()) {
            return null;
        }
        JsonObject object = raw.getAsJsonObject();
        var entry = new LinkedHashMap<String, Object>();
        for (SettingDescriptor field : map.fields()) {
            JsonElement fieldRaw = object.get(field.key());
            Object value = fieldRaw == null ? field.defaultValue() : fromJson(field, fieldRaw);
            if (value == null || !field.accepts(value)) {
                return null;
            }
            entry.put(field.key(), value);
        }
        return Collections.unmodifiableMap(entry);
    }

    /**
     * Writes a changed map setting onto its original JSON: removed keys are deleted, added or changed keys get their
     * declared fields (keeping unknown fields of an original object), and every other member, invalid ones included,
     * stays unchanged.
     */
    @SuppressWarnings("unchecked")
    private static JsonObject mergeMap(SettingDescriptor.MapSetting map, JsonElement original, Object oldValue,
            Object newValue) {
        JsonObject out = original != null && original.isJsonObject() ? original.getAsJsonObject() : new JsonObject();
        var before = (Map<String, Map<String, Object>>) oldValue;
        var after = (Map<String, Map<String, Object>>) newValue;
        for (String key : before.keySet()) {
            if (!after.containsKey(key)) {
                out.remove(key);
            }
        }
        after.forEach((key, entry) -> {
            if (entry.equals(before.get(key))) {
                return;
            }
            JsonElement existing = out.get(key);
            JsonObject target = existing != null && existing.isJsonObject() ? existing.getAsJsonObject()
                    : new JsonObject();
            for (SettingDescriptor field : map.fields()) {
                target.add(field.key(), toJson(entry.get(field.key())));
            }
            out.add(key, target);
        });
        return out;
    }

    private static Object fromJson(SettingDescriptor setting, JsonElement raw) {
        if (!(raw instanceof JsonPrimitive p)) {
            return null;
        }
        return switch (setting) {
            case SettingDescriptor.MapSetting ignored -> null;
            case SettingDescriptor.BooleanSetting ignored -> p.isBoolean() ? p.getAsBoolean() : null;
            case SettingDescriptor.EnumSetting ignored -> p.isString() ? p.getAsString() : null;
            case SettingDescriptor.IntegerSetting ignored -> {
                if (!p.isNumber()) {
                    yield null;
                }
                try {
                    yield new BigDecimal(p.getAsString()).intValueExact();
                } catch (ArithmeticException | NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    private static JsonElement toJson(Object value) {
        return switch (value) {
            case Boolean b -> new JsonPrimitive(b);
            case Integer i -> new JsonPrimitive(i);
            case String s -> new JsonPrimitive(s);
            default -> throw new IllegalArgumentException("Unsupported setting value type: " + value.getClass());
        };
    }

    private static JsonObject parse(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || !element.isJsonObject()) {
                return null;
            }
            return element.getAsJsonObject();
        } catch (JsonParseException | IOException | IllegalStateException e) {
            return null;
        }
    }

    private static Integer schemaVersion(JsonObject document) {
        if (document.get(SCHEMA_VERSION) instanceof JsonPrimitive p && p.isNumber()) {
            try {
                int version = new BigDecimal(p.getAsString()).intValueExact();
                return version >= 1 ? version : null;
            } catch (ArithmeticException | NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String name() {
        return String.valueOf(file.getFileName());
    }

    private void requireLoaded() {
        if (status == null) {
            throw new IllegalStateException("Configuration not loaded");
        }
    }
}
