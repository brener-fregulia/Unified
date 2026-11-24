package io.github.brener_fregulia.qolunified.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.BooleanSetting;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.IntegerSetting;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.MapSetting;
import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The map setting (sound-control-v1.md §8.2): per-entry validation, preservation and round trip. */
class MapSettingConfigTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
    private static final MapSetting RULES = new MapSetting("rules", Pattern.compile("[a-z]+:[a-z.]+"), 3,
            List.of(new IntegerSetting("volume", 100, 0, 100, ApplyMode.LIVE),
                    new BooleanSetting("muted", false, ApplyMode.LIVE)),
            ApplyMode.LIVE);
    private static final FeatureDescriptor DESCRIPTOR = new FeatureDescriptor("gamma", FeatureCategory.AUDIO, true,
            ApplyMode.LIVE, List.of(RULES), List.of());

    @TempDir
    Path dir;
    private Path file;
    private FeatureRegistry registry;

    @BeforeEach
    void setUp() {
        file = dir.resolve("config.json");
        registry = FeatureRegistry.builder().register(ConfigFixtures.feature(DESCRIPTOR)).build();
    }

    private ConfigurationService service() {
        return new ConfigurationService(file, registry, Migrations.production(), new ConfigFileSystem(), CLOCK);
    }

    private static Map<String, Object> entry(int volume, boolean muted) {
        return Map.of("volume", volume, "muted", muted);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> rules(ConfigurationService service) {
        return (Map<String, Map<String, Object>>) service.choices().get("gamma").settings().get("rules");
    }

    private SaveResult save(ConfigurationService service, Map<String, Map<String, Object>> rules) {
        FeatureChoice choice = service.choices().get("gamma");
        return service.save(Map.of("gamma", choice.withSettings(choice.settings().with("rules", rules))));
    }

    private JsonObject fileRules() throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("features")
                .getAsJsonObject("gamma").getAsJsonObject("settings").getAsJsonObject("rules");
    }

    @Test
    void descriptorValidatesItsShape() {
        assertThrows(IllegalArgumentException.class, () -> new MapSetting("m", Pattern.compile(".*"), 0,
                RULES.fields(), ApplyMode.LIVE));
        assertThrows(IllegalArgumentException.class, () -> new MapSetting("m", Pattern.compile(".*"), 1,
                List.of(), ApplyMode.LIVE));
        assertThrows(IllegalArgumentException.class, () -> new MapSetting("m", Pattern.compile(".*"), 1,
                List.of(RULES), ApplyMode.LIVE));
        assertEquals(Map.of(), RULES.defaultValue());
        assertTrue(RULES.accepts(Map.of("a:b", entry(25, true))));
        assertFalse(RULES.accepts(Map.of("no-namespace", entry(25, true))), "key pattern");
        assertFalse(RULES.accepts(Map.of("a:b", entry(101, false))), "range");
        assertFalse(RULES.accepts(Map.of("a:b", Map.of("volume", 5))), "every field is required in memory");
        assertFalse(RULES.accepts(Map.of("a:b", entry(1, false), "a:c", entry(1, false), "a:d", entry(1, false),
                "a:e", entry(1, false))), "limit");
    }

    @Test
    void missingSettingDefaultsToEmptyAndRoundTrips() throws IOException {
        var service = service();
        service.load();
        assertEquals(Map.of(), rules(service));
        assertEquals(SaveResult.SAVED, save(service, Map.of("a:b", entry(25, true))));
        assertEquals(JsonParser.parseString("{\"a:b\":{\"volume\":25,\"muted\":true}}"), fileRules());

        var reloaded = service();
        assertEquals(LoadStatus.LOADED, reloaded.load());
        assertEquals(Map.of("a:b", entry(25, true)), rules(reloaded), "volume survives while muted");
        assertEquals(SaveResult.NO_CHANGE, save(reloaded, Map.of("a:b", entry(25, true))));
    }

    @Test
    void invalidEntriesAreIgnoredAndPreserved() throws IOException {
        Files.writeString(file, """
                {"schemaVersion":1,"features":{"gamma":{"settings":{"rules":{
                  "a:ok":{"volume":40},
                  "nonamespace":{"volume":10,"muted":false},
                  "a:range":{"volume":300,"muted":false},
                  "a:type":{"volume":"x","muted":false},
                  "a:notobject":7,
                  "a:extra":{"volume":60,"muted":true,"future":[1]}}}}}}""");
        var service = service();
        assertEquals(LoadStatus.LOADED, service.load());
        assertEquals(Map.of("a:ok", entry(40, false), "a:extra", entry(60, true)), rules(service),
                "missing fields default; unknown fields ignored");

        var changed = new LinkedHashMap<>(rules(service));
        changed.put("a:extra", entry(60, false));
        changed.remove("a:ok");
        changed.put("a:new", entry(5, false));
        assertEquals(SaveResult.SAVED, save(service, Map.copyOf(changed)));
        assertEquals(JsonParser.parseString("""
                {"nonamespace":{"volume":10,"muted":false},
                 "a:range":{"volume":300,"muted":false},
                 "a:type":{"volume":"x","muted":false},
                 "a:notobject":7,
                 "a:extra":{"volume":60,"muted":false,"future":[1]},
                 "a:new":{"volume":5,"muted":false}}"""), fileRules());
    }

    @Test
    void settingAnInvalidKeyReplacesItsOriginalEntry() throws IOException {
        Files.writeString(file, """
                {"schemaVersion":1,"features":{"gamma":{"settings":{"rules":{"a:range":{"volume":300}}}}}}""");
        var service = service();
        service.load();
        assertEquals(SaveResult.SAVED, save(service, Map.of("a:range", entry(30, false))));
        assertEquals(JsonParser.parseString("{\"a:range\":{\"volume\":30,\"muted\":false}}"), fileRules());
    }

    @Test
    void nonObjectValueUsesDefaultUntilChanged() throws IOException {
        Files.writeString(file, """
                {"schemaVersion":1,"features":{"gamma":{"settings":{"rules":[1,2]}}}}""");
        var service = service();
        service.load();
        assertEquals(Map.of(), rules(service));
        assertEquals(SaveResult.NO_CHANGE, save(service, Map.of()), "an unchanged setting keeps the original");
        assertEquals(SaveResult.SAVED, save(service, Map.of("a:b", entry(0, false))));
        assertEquals(JsonParser.parseString("{\"a:b\":{\"volume\":0,\"muted\":false}}"), fileRules());
    }

    @Test
    void entriesBeyondTheLimitAreIgnoredAndKept() throws IOException {
        Files.writeString(file, """
                {"schemaVersion":1,"features":{"gamma":{"settings":{"rules":{
                  "a:a":{"volume":1},"a:b":{"volume":2},"a:c":{"volume":3},"a:d":{"volume":4}}}}}}""");
        var service = service();
        service.load();
        assertEquals(3, rules(service).size());
        assertFalse(rules(service).containsKey("a:d"), "file order decides which entries fit");
        var changed = new LinkedHashMap<>(rules(service));
        changed.remove("a:a");
        save(service, Map.copyOf(changed));
        assertTrue(fileRules().has("a:d"), "excess entry preserved");
        assertFalse(fileRules().has("a:a"));
    }

    @Test
    void snapshotRejectsInvalidMapValues() {
        assertThrows(IllegalArgumentException.class, () -> SettingsSnapshot.defaults(DESCRIPTOR)
                .with("rules", Map.of("bad", entry(1, false))));
        SettingDescriptor setting = DESCRIPTOR.setting("rules").orElseThrow();
        assertTrue(setting instanceof MapSetting);
    }

    @Test
    void readOnlyConfigurationNeverWritesMapChanges() throws IOException {
        Files.writeString(file, "{\"schemaVersion\":99,\"features\":{\"gamma\":{\"settings\":{\"rules\":"
                + "{\"a:b\":{\"volume\":10,\"muted\":true}}}}}}");
        var service = service();
        assertEquals(LoadStatus.NEWER_VERSION, service.load());
        assertEquals(Map.of("a:b", entry(10, true)), rules(service), "understood values are used");
        assertEquals(SaveResult.READ_ONLY, save(service, Map.of()));
        assertEquals(JsonParser.parseString("{\"a:b\":{\"volume\":10,\"muted\":true}}"), fileRules());
    }
}
