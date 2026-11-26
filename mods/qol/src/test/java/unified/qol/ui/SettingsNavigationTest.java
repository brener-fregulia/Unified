package unified.qol.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.FeatureCategory;
import unified.qol.ui.SettingsNavigation.FeatureEntry;
import unified.qol.ui.SettingsNavigation.Kind;
import unified.qol.ui.SettingsNavigation.Row;
import unified.qol.ui.SettingsNavigation.SettingEntry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsNavigationTest {
    private static final List<FeatureEntry> FEATURES = List.of(
            new FeatureEntry(FeatureCategory.HUD_INFORMATION, "food_hud", "HUD de Comida", "Mostra sua saturação",
                    List.of(new SettingEntry("tooltip", "Valores de comida nas dicas",
                            List.of("Desligado", "Com Shift", "Sempre")),
                            new SettingEntry("saturation_overlay", "Saturação na barra", List.of()))),
            new FeatureEntry(FeatureCategory.HUD_INFORMATION, "target_info", "Target Info", "What you look at",
                    List.of(new SettingEntry("show_mining_progress", "Mining progress", List.of()))),
            new FeatureEntry(FeatureCategory.INVENTORY, "shulker_preview", "Shulker Preview", "Boxes",
                    List.of(new SettingEntry("slot_icon_source", "Content icon",
                            List.of("First occupied slot", "Predominant item")))),
            new FeatureEntry(FeatureCategory.AUDIO, "sound_control", "Sound Control", "Volumes", List.of()));

    private static List<String> visible(SettingsNavigation nav) {
        return nav.rows(id -> false).stream().map(r -> switch (r.kind()) {
            case CATEGORY -> (r.expanded() ? "v " : "> ") + r.category() + " " + r.count();
            case FEATURE -> (r.selected() ? "* " : "  ") + r.featureId();
            case SETTING -> "    " + r.featureId() + "." + r.settingKey();
        }).toList();
    }

    @Test
    void startsOnTheFirstFeatureWithItsCategoryOpenAndHidesEmptyCategories() {
        var nav = new SettingsNavigation(FEATURES, null);
        assertEquals("food_hud", nav.selected());
        assertEquals(List.of("v HUD_INFORMATION 2", "* food_hud", "  target_info", "> INVENTORY 1", "> AUDIO 1"),
                visible(nav));
        assertEquals(List.of(FeatureCategory.HUD_INFORMATION, FeatureCategory.INVENTORY, FeatureCategory.AUDIO),
                nav.categories());
    }

    @Test
    void aContextualEntryPointPreselectsItsFeature() {
        var nav = new SettingsNavigation(FEATURES, "sound_control");
        assertEquals("sound_control", nav.selected());
        assertTrue(nav.isExpanded(FeatureCategory.AUDIO));
        assertEquals("food_hud", new SettingsNavigation(FEATURES, "missing").selected(), "unknown id ignored");
    }

    @Test
    void categoriesExpandAndCollapseAndSeveralStayOpen() {
        var nav = new SettingsNavigation(FEATURES, null);
        nav.clickCategory(FeatureCategory.INVENTORY);
        assertEquals("shulker_preview", nav.selected(), "expanding selects its first feature");
        assertTrue(nav.isExpanded(FeatureCategory.HUD_INFORMATION) && nav.isExpanded(FeatureCategory.INVENTORY));
        nav.clickCategory(FeatureCategory.INVENTORY);
        assertFalse(nav.isExpanded(FeatureCategory.INVENTORY));
        assertEquals("shulker_preview", nav.selected(), "collapsing keeps the selection");
        nav.setExpanded(FeatureCategory.AUDIO, true);
        assertEquals("shulker_preview", nav.selected(), "keyboard expansion does not select");
        nav.select("target_info");
        assertEquals("target_info", nav.selected());
    }

    @Test
    void searchMatchesNamesDescriptionsIdsSettingsAndOptionsIgnoringCaseAndAccents() {
        var nav = new SettingsNavigation(FEATURES, null);
        nav.setQuery("SATURACAO");
        assertEquals(List.of("v HUD_INFORMATION 1", "* food_hud", "    food_hud.saturation_overlay"),
                visible(nav), "description and setting match, without accents");
        nav.setQuery("predominant");
        assertEquals("shulker_preview", nav.selected(), "the panel follows the first match");
        assertEquals(List.of("v INVENTORY 1", "* shulker_preview", "    shulker_preview.slot_icon_source"),
                visible(nav), "an enum option label finds its setting");
        nav.setQuery("com shift");
        assertTrue(visible(nav).contains("    food_hud.tooltip"));
        nav.setQuery("sound_control");
        assertEquals(List.of("v AUDIO 1", "* sound_control"), visible(nav), "ids match");
        nav.setQuery("zzz");
        assertFalse(nav.hasResults());
        assertEquals(List.of(), visible(nav));
        assertEquals("sound_control", nav.selected(), "no result keeps the selection");
    }

    @Test
    void clearingTheSearchRestoresTheExpansionAndKeepsTheChosenFeatureVisible() {
        var nav = new SettingsNavigation(FEATURES, null);
        nav.setQuery("mining");
        nav.select("target_info");
        nav.setQuery("");
        assertEquals(List.of("v HUD_INFORMATION 2", "  food_hud", "* target_info", "> INVENTORY 1", "> AUDIO 1"),
                visible(nav));
        nav.setQuery("content icon");
        nav.select("shulker_preview");
        nav.setQuery("");
        assertTrue(nav.isExpanded(FeatureCategory.INVENTORY), "the selected result's category opens");
        assertTrue(nav.isExpanded(FeatureCategory.HUD_INFORMATION), "earlier state kept");
    }

    @Test
    void featuresWithInvalidInputAreFlagged() {
        var nav = new SettingsNavigation(FEATURES, null);
        List<Row> rows = nav.rows("target_info"::equals);
        Optional<Row> flagged = rows.stream().filter(Row::flagged).findFirst();
        assertEquals("target_info", flagged.orElseThrow().featureId());
        assertEquals(Kind.FEATURE, flagged.get().kind());
    }

    @Test
    void navigationNeverNeedsADraftOrConfiguration() {
        // The model only holds strings and enum values; this documents that it can be built and searched alone.
        var nav = new SettingsNavigation(List.of(), null);
        assertEquals(null, nav.selected());
        assertEquals(List.of(), nav.rows(id -> true));
        assertEquals("acao", SettingsNavigation.normalize("  AÇÃO "));
    }
}
