package unified.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The module's independence from Unified: QoL and its own metadata, files and translations. */
class FoundationTest {
    private JsonObject json(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            return new Gson().fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    @Test
    void metadataIsIndependentAndClientOnly() throws Exception {
        JsonObject mod = json("/fabric.mod.json");
        assertEquals("unified_optimizations", mod.get("id").getAsString());
        assertEquals("Unified: Optimizations", mod.get("name").getAsString());
        assertEquals("client", mod.get("environment").getAsString());
        assertFalse(mod.get("version").getAsString().contains("$"), "the build expands the module's own version");
        JsonObject depends = mod.getAsJsonObject("depends");
        assertFalse(depends.has("qol_unified"), "never requires Unified: QoL");
        assertFalse(mod.has("recommends") && mod.getAsJsonObject("recommends").has("qol_unified"));
        assertEquals(List.of("fabricloader", "minecraft", "java", "fabric-language-kotlin", "unified_settings"),
                List.copyOf(depends.keySet()));
        assertEquals(">=1.0.0 <2.0.0", depends.get("unified_settings").getAsString(),
                "the library contract compiled against, up to its next major");
        assertFalse(depends.get("fabric-language-kotlin").getAsString().contains("$"));
    }

    @Test
    void javaCallsTheKotlinStartupReport() {
        String message = StartupReport.message(Path.of("config"));
        assertTrue(message.endsWith(OptimizationsPaths.configDirectory(Path.of("config")).toString()));
    }

    @Test
    void ownsItsConfigurationDirectory() {
        Path config = Path.of("config");
        assertEquals(config.resolve("unified_optimizations"), OptimizationsPaths.configDirectory(config));
        assertEquals(config.resolve("unified_optimizations").resolve("config.json"),
                OptimizationsPaths.configFile(config));
        assertFalse(OptimizationsPaths.configDirectory(config).startsWith(config.resolve("qol_unified")));
    }

    @Test
    void textsExistInEnglishAndPortuguese() throws Exception {
        var en = json("/assets/unified_optimizations/lang/en_us.json").keySet();
        var pt = json("/assets/unified_optimizations/lang/pt_br.json").keySet();
        assertEquals(en, pt);
        assertTrue(en.contains("modmenu.nameTranslation.unified_optimizations"));
    }
}
