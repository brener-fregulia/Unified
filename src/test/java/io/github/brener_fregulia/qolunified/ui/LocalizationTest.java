package io.github.brener_fregulia.qolunified.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.brener_fregulia.qolunified.Features;
import io.github.brener_fregulia.qolunified.core.Feature;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.feature.foodhud.FoodHudSettings;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyboardMapTexts;
import io.github.brener_fregulia.qolunified.feature.soundcontrol.SoundControlTexts;
import io.github.brener_fregulia.qolunified.feature.targetinfo.TargetInfoTexts;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** AC-12: every translation key used by the UI and by registered features exists in both languages. */
class LocalizationTest {
    private static JsonObject load(String language) throws Exception {
        String path = "/assets/qol_unified/lang/" + language + ".json";
        try (var stream = LocalizationTest.class.getResourceAsStream(path)) {
            assertTrue(stream != null, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<String> requiredKeys() throws Exception {
        var keys = new ArrayList<String>();
        for (var field : UiKeys.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                keys.add((String) field.get(null));
            }
        }
        for (FeatureCategory category : FeatureCategory.values()) {
            keys.add(UiKeys.category(category));
        }
        for (Feature feature : Features.createRegistry().features()) {
            FeatureDescriptor d = feature.descriptor();
            keys.add(d.nameKey());
            keys.add(d.descriptionKey());
            d.conflicts().forEach(c -> keys.add(c.reasonKey()));
            for (SettingDescriptor setting : d.settings()) {
                keys.add(setting.nameKey(d.id()));
                if (setting instanceof SettingDescriptor.EnumSetting e) {
                    e.values().forEach(v -> keys.add(UiKeys.enumValue(d.id(), e.key(), v)));
                }
            }
        }
        keys.add(FoodHudSettings.DEBUG_LINE_KEY);
        for (var field : TargetInfoTexts.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                keys.add((String) field.get(null));
            }
        }
        for (var field : SoundControlTexts.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                keys.add((String) field.get(null));
            }
        }
        for (var field : KeyboardMapTexts.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                keys.add((String) field.get(null));
            }
        }
        return keys;
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws Exception {
        assertEquals(new TreeSet<>(load("en_us").keySet()), new TreeSet<>(load("pt_br").keySet()));
    }

    @Test
    void everyRequiredKeyIsTranslated() throws Exception {
        for (String language : List.of("en_us", "pt_br")) {
            Set<String> present = load(language).keySet();
            for (String key : requiredKeys()) {
                assertTrue(present.contains(key), language + " is missing " + key);
                assertTrue(!load(language).get(key).getAsString().isBlank(), language + " has blank " + key);
            }
        }
    }
}
