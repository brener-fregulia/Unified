package unified.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import unified.settings.ui.WorkspaceNavigation
import unified.settings.ui.WorkspaceNavigation.Feature
import unified.settings.ui.WorkspaceNavigation.Kind
import unified.settings.ui.WorkspaceNavigation.Module
import unified.settings.ui.WorkspaceNavigation.Setting

/** The shared sidebar model (unified-settings-integration.md §20.3), ported from QoL's SettingsNavigationTest. */
class WorkspaceNavigationTest {
    private val qolFeatures = listOf(
        Feature("hud", "k.hud", "food_hud", "k.food", "HUD de Comida", "Mostra sua saturação", listOf(
            Setting("tooltip", "k.t", "Valores de comida nas dicas", listOf("Desligado", "Com Shift", "Sempre")),
            Setting("saturation_overlay", "k.s", "Saturação na barra"))),
        Feature("hud", "k.hud", "target_info", "k.ti", "Target Info", "What you look at",
            listOf(Setting("show_mining_progress", "k.m", "Mining progress"))),
        Feature("inventory", "k.inv", "shulker_preview", "k.sp", "Shulker Preview", "Boxes",
            listOf(Setting("slot_icon_source", "k.i", "Content icon", listOf("First occupied slot", "Predominant item")))),
        Feature("audio", "k.audio", "sound_control", "k.sc", "Sound Control", "Volumes"))
    private val qol = Module("qol_unified", "k.qol", qolFeatures)
    private val optimizations = Module("unified_optimizations", "k.opt", emptyList())

    private fun visible(nav: WorkspaceNavigation): List<String> = nav.rows { _, _ -> false }.map { r ->
        val pad = "  ".repeat(r.depth)
        pad + when (r.kind) {
            Kind.MODULE -> (if (r.expanded) "V " else "> ") + r.moduleId
            Kind.CATEGORY -> (if (r.expanded) "v " else "> ") + r.categoryId + " " + r.count
            Kind.FEATURE -> (if (r.selected) "* " else "  ") + r.featureId
            Kind.SETTING -> "    " + r.featureId + "." + r.settingKey
            Kind.NOTE -> "- " + r.note
        }
    }

    @Test
    fun singleModuleShowsItsCategoriesDirectlyLikeTheQolWorkspace() {
        val nav = WorkspaceNavigation(listOf(qol), null, null)
        assertFalse(nav.showsModules())
        assertEquals("food_hud", nav.selected())
        assertEquals(listOf("v hud 2", "* food_hud", "  target_info", "> inventory 1", "> audio 1"), visible(nav))
    }

    @Test
    fun bothModulesAddATopLevelRowAndOptimizationsSaysItHasNoFeatures() {
        val nav = WorkspaceNavigation(listOf(qol, optimizations), null, null)
        assertTrue(nav.showsModules())
        assertEquals(listOf("V qol_unified", "  v hud 2", "  * food_hud", "    target_info", "  > inventory 1",
            "  > audio 1", "V unified_optimizations", "  - NO_FEATURES"), visible(nav))
        nav.clickModule("qol_unified")
        assertEquals(listOf("> qol_unified", "V unified_optimizations", "  - NO_FEATURES"), visible(nav))
        assertEquals("food_hud", nav.selected(), "collapsing a module keeps the selection")
        nav.clickModule("qol_unified")
        assertTrue(nav.isModuleExpanded("qol_unified"))
    }

    @Test
    fun optimizationsOnlyHasNoFeaturesAndNoRows() {
        val nav = WorkspaceNavigation(listOf(optimizations), null, null)
        assertFalse(nav.hasFeatures())
        assertEquals(emptyList<String>(), visible(nav))
        assertEquals(null, nav.selected())
    }

    @Test
    fun aContextualEntryPointPreselectsItsFeature() {
        val nav = WorkspaceNavigation(listOf(qol, optimizations), "qol_unified", "sound_control")
        assertEquals("sound_control", nav.selected())
        assertEquals("qol_unified", nav.selectedModule())
        assertTrue(nav.isExpanded("qol_unified", "audio"))
        assertEquals("food_hud", WorkspaceNavigation(listOf(qol), "qol_unified", "missing").selected())
    }

    @Test
    fun categoriesExpandAndCollapseAndSeveralStayOpen() {
        val nav = WorkspaceNavigation(listOf(qol), null, null)
        nav.clickCategory("qol_unified", "inventory")
        assertEquals("shulker_preview", nav.selected(), "expanding selects its first feature")
        assertTrue(nav.isExpanded("hud") && nav.isExpanded("inventory"))
        nav.clickCategory("qol_unified", "inventory")
        assertFalse(nav.isExpanded("inventory"))
        assertEquals("shulker_preview", nav.selected(), "collapsing keeps the selection")
        nav.setExpanded("qol_unified", "audio", true)
        assertEquals("shulker_preview", nav.selected(), "keyboard expansion does not select")
    }

    @Test
    fun searchSpansModulesShowsTheOwningModuleAndRestoresExpansion() {
        val other = Module("graphics", "k.g", listOf(Feature("look", "k.l", "shaders", "k.sh", "Shaders",
            "Saturation of colors")))
        val nav = WorkspaceNavigation(listOf(qol, optimizations, other), null, null)
        nav.setQuery("SATURACAO")
        assertEquals(listOf("V qol_unified", "  v hud 1", "  * food_hud", "      food_hud.saturation_overlay"),
            visible(nav), "accent-insensitive; modules without matches are hidden")
        nav.setQuery("saturation")
        assertEquals(listOf("V qol_unified", "  v hud 1", "  * food_hud", "      food_hud.saturation_overlay",
            "V graphics", "  v look 1", "    shaders"), visible(nav), "results from every module, under it")
        nav.setQuery("predominant")
        assertEquals("shulker_preview", nav.selected(), "the panel follows the first match")
        nav.setQuery("nothing at all")
        assertFalse(nav.hasResults())
        nav.setQuery("")
        assertTrue(nav.isExpanded("qol_unified", "hud") && nav.isExpanded("qol_unified", "inventory"),
            "clearing restores the expansion plus the selection's category")
        assertFalse(nav.isExpanded("qol_unified", "audio"))
    }

    @Test
    fun sameFeatureIdInTwoModulesStaysDistinct() {
        val a = Module("a", "k.a", listOf(Feature("c", "k.c", "x", "k.x", "X", "")))
        val b = Module("b", "k.b", listOf(Feature("c", "k.c", "x", "k.x", "X", "")))
        val nav = WorkspaceNavigation(listOf(a, b), "b", "x")
        assertEquals("b", nav.selectedModule())
        assertEquals(1, nav.rows { _, _ -> false }.count { it.selected })
    }
}
