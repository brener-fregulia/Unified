package unified.qol.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.SettingDescriptor.BooleanSetting;
import unified.qol.core.SettingDescriptor.EnumSetting;
import unified.qol.core.SettingDescriptor.IntegerSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class FeatureDescriptorTest {
    private static FeatureDescriptor descriptor(String id, List<SettingDescriptor> settings) {
        return new FeatureDescriptor(id, FeatureCategory.INVENTORY, true, ApplyMode.LIVE, settings, List.of());
    }

    @Test
    void validDescriptorDerivesTranslationKeys() {
        FeatureDescriptor d = descriptor("food_hud", List.of(new BooleanSetting("show", true, ApplyMode.LIVE)));
        assertEquals("qol_unified.feature.food_hud.name", d.nameKey());
        assertEquals("qol_unified.feature.food_hud.description", d.descriptionKey());
        assertEquals("qol_unified.feature.food_hud.setting.show", d.settings().getFirst().nameKey("food_hud"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "Food", "1food", "food-hud", "food.hud", "performance.fast_startup"})
    void invalidFeatureIdsAreRejected(String id) {
        assertThrows(IllegalArgumentException.class, () -> descriptor(id, List.of()));
    }

    @Test
    void longestValidIdIsAccepted() {
        descriptor("a".repeat(64), List.of());
        assertThrows(IllegalArgumentException.class, () -> descriptor("a".repeat(65), List.of()));
    }

    @Test
    void duplicateSettingKeysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> descriptor("food_hud", List.of(
                new BooleanSetting("show", true, ApplyMode.LIVE),
                new BooleanSetting("show", false, ApplyMode.LIVE))));
    }

    @Test
    void invalidSettingDescriptorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BooleanSetting("Bad", true, ApplyMode.LIVE));
        assertThrows(IllegalArgumentException.class, () -> new IntegerSetting("size", 11, 1, 10, ApplyMode.LIVE));
        assertThrows(IllegalArgumentException.class, () -> new IntegerSetting("size", 5, 10, 1, ApplyMode.LIVE));
        assertThrows(IllegalArgumentException.class,
                () -> new EnumSetting("mode", "c", List.of("a", "b"), ApplyMode.LIVE));
        assertThrows(IllegalArgumentException.class,
                () -> new EnumSetting("mode", "a", List.of("a", "a"), ApplyMode.LIVE));
        assertThrows(NullPointerException.class, () -> new BooleanSetting("show", null, ApplyMode.LIVE));
    }

    @Test
    void settingValidationFollowsTypeAndRange() {
        var size = new IntegerSetting("size", 5, 1, 10, ApplyMode.LIVE);
        assertTrue(size.accepts(10));
        assertTrue(!size.accepts(11) && !size.accepts("5") && !size.accepts(null));
        var mode = new EnumSetting("mode", "a", List.of("a", "b"), ApplyMode.LIVE);
        assertTrue(mode.accepts("b") && !mode.accepts("c"));
    }

    @Test
    void descriptorListsAreImmutableCopies() {
        var settings = new ArrayList<SettingDescriptor>(List.of(new BooleanSetting("show", true, ApplyMode.LIVE)));
        FeatureDescriptor d = descriptor("food_hud", settings);
        settings.clear();
        assertEquals(1, d.settings().size());
        assertThrows(UnsupportedOperationException.class, () -> d.settings().clear());
    }

    @Test
    void snapshotUsesDefaultsAndRejectsInvalidValues() {
        FeatureDescriptor d = descriptor("food_hud", List.of(new IntegerSetting("size", 5, 1, 10, ApplyMode.LIVE)));
        assertEquals(5, SettingsSnapshot.defaults(d).getInt("size"));
        assertEquals(7, SettingsSnapshot.of(d, Map.of("size", 7)).getInt("size"));
        assertThrows(IllegalArgumentException.class, () -> SettingsSnapshot.of(d, Map.of("size", 99)));
        assertThrows(IllegalArgumentException.class, () -> SettingsSnapshot.of(d, Map.of("other", 1)));
        assertThrows(UnsupportedOperationException.class, () -> SettingsSnapshot.defaults(d).values().put("size", 1));
    }
}
