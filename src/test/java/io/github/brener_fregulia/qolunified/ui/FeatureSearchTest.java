package io.github.brener_fregulia.qolunified.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureSearchTest {
    private static final Map<String, String> TEXT = Map.of(
            "qol_unified.feature.food_hud.name", "Informações de Comida",
            "qol_unified.feature.food_hud.description", "Shows hunger and saturation",
            "qol_unified.feature.sorting.name", "Inventory Sorting",
            "qol_unified.feature.sorting.description", "Sorts containers",
            "qol_unified.feature.minimap.name", "Minimap",
            "qol_unified.feature.minimap.description", "Shows nearby terrain");

    private static FeatureRegistry registry() {
        return FeatureRegistry.builder()
                .register(UiFixtures.feature("minimap", FeatureCategory.MAPS_NAVIGATION))
                .register(UiFixtures.feature("sorting", FeatureCategory.INVENTORY))
                .register(UiFixtures.feature("food_hud", FeatureCategory.HUD_INFORMATION))
                .build();
    }

    private static List<String> ids(List<FeatureSearch.Row> rows) {
        return rows.stream().map(r -> r.header() ? "#" + r.category() : r.feature().id()).toList();
    }

    @Test
    void matchesNameDescriptionAndIdCaseInsensitively() {
        assertTrue(FeatureSearch.matches("COMIDA", "Informações de Comida", "", "food_hud"));
        assertTrue(FeatureSearch.matches("saturation", "x", "Shows hunger and saturation", "food_hud"));
        assertTrue(FeatureSearch.matches("FOOD_H", "x", "y", "food_hud"));
        assertTrue(FeatureSearch.matches("  ", "x", "y", "z"), "blank query matches everything");
        assertFalse(FeatureSearch.matches("map", "Food", "Hunger", "food_hud"));
    }

    @Test
    void categoriesOnlyIncludeUsedOnesInCatalogOrder() {
        assertEquals(List.of(FeatureCategory.HUD_INFORMATION, FeatureCategory.INVENTORY,
                FeatureCategory.MAPS_NAVIGATION), FeatureSearch.categories(registry()));
        assertTrue(FeatureSearch.categories(FeatureRegistry.builder().build()).isEmpty());
    }

    @Test
    void blankQueryShowsSelectedCategoryWithoutHeaders() {
        assertEquals(List.of("sorting"),
                ids(FeatureSearch.rows(registry(), FeatureCategory.INVENTORY, "", TEXT::get)));
    }

    @Test
    void searchSpansCategoriesWithCategoryHeaders() {
        assertEquals(List.of("#HUD_INFORMATION", "food_hud", "#MAPS_NAVIGATION", "minimap"),
                ids(FeatureSearch.rows(registry(), FeatureCategory.INVENTORY, "shows", TEXT::get)));
        assertTrue(FeatureSearch.rows(registry(), FeatureCategory.INVENTORY, "nothing", TEXT::get).isEmpty());
    }
}
