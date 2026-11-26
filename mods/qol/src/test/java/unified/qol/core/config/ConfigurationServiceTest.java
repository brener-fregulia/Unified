package unified.qol.core.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import unified.qol.core.FeatureRegistry;
import unified.qol.core.SettingsSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigurationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;
    Path file;
    FeatureRegistry registry;
    ConfigFixtures.FaultyFileSystem fs;

    @BeforeEach
    void setUp() {
        file = dir.resolve("config").resolve("qol_unified").resolve("config.json");
        registry = ConfigFixtures.registry();
        fs = new ConfigFixtures.FaultyFileSystem();
    }

    private ConfigurationService service() {
        return service(Migrations.production());
    }

    private ConfigurationService service(Migrations migrations) {
        return new ConfigurationService(file, registry, migrations, fs, CLOCK);
    }

    private void writeFile(String json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }

    private JsonObject readFile() throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private List<Path> backups() throws IOException {
        Path backups = file.resolveSibling("backups");
        if (!Files.exists(backups)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(backups)) {
            return files.toList();
        }
    }

    private SettingsSnapshot settings(ConfigurationService service, String id) {
        return service.choices().get(id).settings();
    }

    @Test
    void missingFileUsesDefaultsAndFirstSaveCreatesIt() throws IOException {
        var service = service();
        assertEquals(LoadStatus.MISSING, service.load());
        var alpha = service.choices().get("alpha");
        assertTrue(alpha.enabled());
        assertFalse(alpha.explicitEnabled());
        assertEquals(5, alpha.settings().getInt("restart_size"));
        assertFalse(Files.exists(file), "loading never writes");

        assertEquals(SaveResult.SAVED, service.save(Map.of()));
        assertEquals(JsonParser.parseString("{\"schemaVersion\":1}"), readFile());
        assertEquals(SaveResult.NO_CHANGE, service.save(Map.of()));
        assertEquals(SaveResult.NO_CHANGE, service.save(service.choices()), "defaults are not written");
    }

    @Test
    void validFileRoundTripsAndUnchangedSaveIsNoOp() throws IOException {
        String json = """
                {"schemaVersion":1,"features":{"alpha":{"enabled":false,
                "settings":{"live_flag":true,"restart_size":7,"mode":"b"}}}}""";
        writeFile(json);
        var service = service();
        assertEquals(LoadStatus.LOADED, service.load());
        var alpha = service.choices().get("alpha");
        assertFalse(alpha.enabled());
        assertTrue(alpha.explicitEnabled());
        assertEquals(Map.of("live_flag", true, "restart_size", 7, "mode", "b"), alpha.settings().values());
        byte[] before = Files.readAllBytes(file);

        assertEquals(SaveResult.NO_CHANGE, service.save(service.choices()));
        assertArrayEquals(before, Files.readAllBytes(file));

        var changed = alpha.withSettings(alpha.settings().with("restart_size", 9));
        assertEquals(SaveResult.SAVED, service.save(Map.of("alpha", changed)));
        JsonObject expected = JsonParser.parseString(json).getAsJsonObject();
        expected.getAsJsonObject("features").getAsJsonObject("alpha").getAsJsonObject("settings")
                .addProperty("restart_size", 9);
        assertEquals(expected, readFile());
        assertEquals(LoadStatus.LOADED, service().load(), "saved file is valid");
    }

    @Test
    void unknownAndNestedDataIsPreserved() throws IOException {
        String json = """
                {"schemaVersion":1,"comment":"keep me","extra":{"nested":[1,2.5,{"deep":null}],"flag":true},
                 "features":{
                   "alpha":{"enabled":true,"note":{"x":[]},"settings":{"live_flag":false,"unknown_key":{"a":[1]}}},
                   "removed_feature":{"enabled":false,"settings":{"anything":[{"k":"v"}]}}}}""";
        writeFile(json);
        var service = service();
        service.load();
        var alpha = service.choices().get("alpha");
        assertEquals(SaveResult.SAVED, service.save(Map.of("alpha", alpha.withSettings(alpha.settings().with("mode", "b")))));

        JsonObject expected = JsonParser.parseString(json).getAsJsonObject();
        expected.getAsJsonObject("features").getAsJsonObject("alpha").getAsJsonObject("settings")
                .addProperty("mode", "b");
        assertEquals(expected, readFile());
        assertTrue(Files.readString(file).contains("2.5"), "numbers keep their original form");
    }

    @Test
    void invalidValuesFallBackToDefaultsAndArePreservedUntilChanged() throws IOException {
        String json = """
                {"schemaVersion":1,"features":{"beta":{"enabled":"yes",
                "settings":{"live_flag":1,"restart_size":99,"mode":"z"}},
                "alpha":{"settings":{"restart_size":2.5}}}}""";
        writeFile(json);
        var service = service();
        assertEquals(LoadStatus.LOADED, service.load());
        var beta = service.choices().get("beta");
        assertFalse(beta.enabled());
        assertFalse(beta.explicitEnabled(), "invalid enabled is not an explicit choice");
        assertEquals(SettingsSnapshot.defaults(beta.settings().descriptor()), beta.settings());
        assertEquals(5, settings(service, "alpha").getInt("restart_size"));

        assertEquals(SaveResult.SAVED, service.save(Map.of("beta", beta.withSettings(beta.settings().with("mode", "b")))));
        JsonObject expected = JsonParser.parseString(json).getAsJsonObject();
        expected.getAsJsonObject("features").getAsJsonObject("beta").getAsJsonObject("settings")
                .addProperty("mode", "b");
        assertEquals(expected, readFile(), "only the changed setting is overwritten");

        var reloaded = service();
        reloaded.load();
        var b = reloaded.choices().get("beta");
        assertEquals(SaveResult.SAVED, reloaded.save(Map.of("beta", b.withEnabled(true)
                .withSettings(b.settings().with("restart_size", 3)))));
        var betaJson = readFile().getAsJsonObject("features").getAsJsonObject("beta");
        assertEquals(true, betaJson.get("enabled").getAsBoolean());
        assertEquals(3, betaJson.getAsJsonObject("settings").get("restart_size").getAsInt());
        assertEquals(1, betaJson.getAsJsonObject("settings").get("live_flag").getAsInt(), "still preserved");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "{", "{\"schemaVersion\":1,}", "[1,2]", "\"text\"", "{\"features\":{}}",
            "{\"schemaVersion\":\"1\"}", "{\"schemaVersion\":0}", "{\"schemaVersion\":1.5}",
            "{\"schemaVersion\":1,\"features\":[]}", "{\"schemaVersion\":1} trailing", "// c\n{\"schemaVersion\":1}"})
    void corruptFileIsBackedUpAndReplacedOnlyOnSave(String content) throws IOException {
        writeFile(content);
        byte[] original = Files.readAllBytes(file);
        var service = service();
        assertEquals(LoadStatus.RECOVERED, service.load());
        assertFalse(service.readOnly());
        List<Path> backups = backups();
        assertEquals(1, backups.size());
        assertEquals("config-20261121T120000Z-corrupt.json", backups.getFirst().getFileName().toString());
        assertArrayEquals(original, Files.readAllBytes(backups.getFirst()));
        assertArrayEquals(original, Files.readAllBytes(file), "original untouched until save");

        assertEquals(SaveResult.SAVED, service.save(Map.of()));
        assertEquals(JsonParser.parseString("{\"schemaVersion\":1}"), readFile());
    }

    @Test
    void invalidUtf8IsCorrupt() throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {'{', (byte) 0xC3, '}'});
        assertEquals(LoadStatus.RECOVERED, service().load());
    }

    @Test
    void repeatedBackupsDoNotOverwriteEachOther() throws IOException {
        writeFile("{");
        service().load();
        service().load();
        assertEquals(2, backups().size());
    }

    @Test
    void backupFailurePreventsAnyWrite() throws IOException {
        writeFile("{ not json");
        fs.failCopy = true;
        var service = service();
        assertEquals(LoadStatus.RECOVERY_BLOCKED, service.load());
        assertTrue(service.readOnly());
        assertEquals(SaveResult.READ_ONLY, service.save(Map.of()));
        assertEquals("{ not json", Files.readString(file));
    }

    @Test
    void olderSchemaIsBackedUpAndMigratedPreservingOtherData() throws IOException {
        writeFile("""
                {"schemaVersion":1,"keep":{"a":[1]},"features":{"alpha":{"settings":{"old_size":8}}}}""");
        Migration renameSize = new Migration() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public JsonObject migrate(JsonObject root) {
                JsonObject settings = root.getAsJsonObject("features").getAsJsonObject("alpha")
                        .getAsJsonObject("settings");
                settings.add("restart_size", settings.remove("old_size"));
                return root;
            }
        };
        var service = service(new Migrations(2, List.of(renameSize)));
        String before = Files.readString(file);
        assertEquals(LoadStatus.MIGRATED, service.load());
        assertEquals(8, settings(service, "alpha").getInt("restart_size"));
        assertEquals(before, Files.readString(file), "migration happens in memory");
        assertEquals("config-20261121T120000Z-pre-migration.json", backups().getFirst().getFileName().toString());
        assertEquals(before, Files.readString(backups().getFirst()));

        assertEquals(SaveResult.SAVED, service.save(Map.of()), "an unchanged save still writes the migrated file");
        assertEquals(JsonParser.parseString("""
                {"schemaVersion":2,"keep":{"a":[1]},"features":{"alpha":{"settings":{"restart_size":8}}}}"""),
                readFile());
    }

    @Test
    void failedMigrationIsReadOnlyAndLeavesFileUntouched() throws IOException {
        writeFile("{\"schemaVersion\":1,\"features\":{\"alpha\":{\"enabled\":false}}}");
        String before = Files.readString(file);
        Migration broken = new Migration() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public JsonObject migrate(JsonObject root) {
                root.addProperty("partial", true);
                throw new IllegalStateException("broken");
            }
        };
        var service = service(new Migrations(2, List.of(broken)));
        assertEquals(LoadStatus.MIGRATION_FAILED, service.load());
        assertTrue(service.readOnly());
        assertTrue(service.choices().get("alpha").enabled(), "defaults in use");
        assertEquals(SaveResult.READ_ONLY, service.save(Map.of()));
        assertEquals(before, Files.readString(file));

        Migration first = new Migration() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public JsonObject migrate(JsonObject root) {
                return root;
            }
        };
        assertEquals(LoadStatus.MIGRATION_FAILED, service(new Migrations(3, List.of(first))).load(),
                "missing step 2 -> 3");
    }

    @Test
    void migrationBackupFailureBlocksMigration() throws IOException {
        writeFile("{\"schemaVersion\":1}");
        fs.failCopy = true;
        Migration identity = new Migration() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public JsonObject migrate(JsonObject root) {
                return root;
            }
        };
        var service = service(new Migrations(2, List.of(identity)));
        assertEquals(LoadStatus.RECOVERY_BLOCKED, service.load());
        assertEquals(SaveResult.READ_ONLY, service.save(Map.of()));
    }

    @Test
    void newerSchemaIsReadOnlyButUnderstoodValuesAreUsed() throws IOException {
        writeFile("{\"schemaVersion\":99,\"future\":{},\"features\":{\"alpha\":{\"enabled\":false,"
                + "\"settings\":{\"restart_size\":3}}}}");
        String before = Files.readString(file);
        var service = service();
        assertEquals(LoadStatus.NEWER_VERSION, service.load());
        assertTrue(service.readOnly());
        assertFalse(service.choices().get("alpha").enabled());
        assertEquals(3, settings(service, "alpha").getInt("restart_size"));
        var alpha = service.choices().get("alpha");
        assertEquals(SaveResult.READ_ONLY, service.save(Map.of("alpha", alpha.withEnabled(true))));
        assertEquals(before, Files.readString(file));
        assertTrue(backups().isEmpty());
    }

    @Test
    void readFailureIsReadOnly() throws IOException {
        writeFile("{\"schemaVersion\":1}");
        fs.failRead = true;
        var service = service();
        assertEquals(LoadStatus.READ_FAILED, service.load());
        assertEquals(SaveResult.READ_ONLY, service.save(Map.of()));
    }

    @Test
    void writeFailurePreservesOriginalAndState() throws IOException {
        writeFile("{\"schemaVersion\":1,\"features\":{\"alpha\":{\"enabled\":true}}}");
        String before = Files.readString(file);
        var service = service();
        service.load();
        var alpha = service.choices().get("alpha");
        fs.failWrite = true;
        assertEquals(SaveResult.FAILED, service.save(Map.of("alpha", alpha.withEnabled(false))));
        assertEquals(before, Files.readString(file));
        assertFalse(Files.exists(file.resolveSibling("config.json.tmp")), "temporary file removed");
        assertTrue(service.choices().get("alpha").enabled(), "in-memory choice unchanged");

        fs.failWrite = false;
        assertEquals(SaveResult.SAVED, service.save(Map.of("alpha", alpha.withEnabled(false))));
        assertFalse(service.choices().get("alpha").enabled());
    }

    @Test
    void failedAtomicMovePreservesOriginal() throws IOException {
        writeFile("{\"schemaVersion\":1}");
        String before = Files.readString(file);
        var service = service();
        service.load();
        fs.failMove = true;
        var beta = service.choices().get("beta");
        assertEquals(SaveResult.FAILED, service.save(Map.of("beta", beta.withEnabled(true))));
        assertEquals(before, Files.readString(file));
        assertFalse(Files.exists(file.resolveSibling("config.json.tmp")));
    }

    @Test
    void atomicReplacementLeavesNoTemporaryFile() throws IOException {
        writeFile("{\"schemaVersion\":1}");
        var service = service();
        service.load();
        var beta = service.choices().get("beta");
        assertEquals(SaveResult.SAVED, service.save(Map.of("beta", beta.withEnabled(true))));
        assertEquals(0, fs.nonAtomicMoves);
        try (Stream<Path> files = Files.list(file.getParent())) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test
    void unsupportedAtomicMoveFallsBackToReplace() throws IOException {
        var service = service();
        service.load();
        fs.atomicUnsupported = true;
        assertEquals(SaveResult.SAVED, service.save(Map.of()));
        assertEquals(1, fs.nonAtomicMoves);
        assertTrue(Files.exists(file));
    }

    @Test
    void explicitEnableChoiceIsPersistedEvenWhenEqualToDefault() throws IOException {
        var service = service();
        service.load();
        var alpha = service.choices().get("alpha");
        assertTrue(alpha.enabled());
        service.save(Map.of("alpha", alpha));
        assertFalse(Files.readString(file).contains("enabled"), "implicit default not written");

        assertEquals(SaveResult.SAVED, service.save(Map.of("alpha", alpha.withEnabled(true))));
        assertTrue(readFile().getAsJsonObject("features").getAsJsonObject("alpha").get("enabled").getAsBoolean());
        var reloaded = service();
        reloaded.load();
        assertTrue(reloaded.choices().get("alpha").explicitEnabled());
    }

    @Test
    void disablingKeepsSettings() throws IOException {
        writeFile("{\"schemaVersion\":1,\"features\":{\"alpha\":{\"settings\":{\"restart_size\":7}}}}");
        var service = service();
        service.load();
        service.save(Map.of("alpha", service.choices().get("alpha").withEnabled(false)));
        var alphaJson = readFile().getAsJsonObject("features").getAsJsonObject("alpha");
        assertFalse(alphaJson.get("enabled").getAsBoolean());
        assertEquals(7, alphaJson.getAsJsonObject("settings").get("restart_size").getAsInt());
    }

    @Test
    void invalidDraftIsRejectedWithoutWriting() {
        var service = service();
        service.load();
        var foreign = new FeatureChoice(true, true,
                SettingsSnapshot.defaults(ConfigFixtures.descriptor("alpha", true)));
        assertThrows(IllegalArgumentException.class, () -> service.save(Map.of("alpha", foreign)));
        assertThrows(IllegalArgumentException.class, () -> service.save(Map.of("missing", foreign)));
        assertFalse(Files.exists(file));
    }

    @Test
    void savedFileIsUtf8PrettyJson() throws IOException {
        var service = service();
        service.load();
        service.save(Map.of());
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(text.startsWith("{\n") && text.endsWith("}\n"));
    }
}
