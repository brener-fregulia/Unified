package unified.settings

import net.minecraft.client.gui.screens.Screen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import unified.settings.api.ModuleOrder
import unified.settings.api.UnifiedSettingsProvider
import unified.settings.registry.ProviderRegistry
import unified.settings.registry.ProviderRegistry.Candidate

class ProviderRegistryTest {
    private fun provider(id: String, order: Int) = object : UnifiedSettingsProvider {
        override val moduleId = id
        override val nameKey = "name.$id"
        override val order = order
        override fun openSettings(parent: Screen?): Screen? = null
    }

    @Test
    fun ordersByApprovedModuleOrderNotLoadOrder() {
        val modules = ProviderRegistry.resolve(listOf(
            Candidate("unified_graphics") { provider("unified_graphics", ModuleOrder.GRAPHICS) },
            Candidate("unified_optimizations") { provider("unified_optimizations", ModuleOrder.OPTIMIZATIONS) },
            Candidate("qol_unified") { provider("qol_unified", ModuleOrder.QOL) },
        ))
        assertEquals(listOf("qol_unified", "unified_optimizations", "unified_graphics"), modules.map { it.modId })
    }

    @Test
    fun equalOrdersFallBackToModId() {
        val modules = ProviderRegistry.resolve(listOf(
            Candidate("b_mod") { provider("b_mod", 50) },
            Candidate("a_mod") { provider("a_mod", 50) },
        ))
        assertEquals(listOf("a_mod", "b_mod"), modules.map { it.modId })
    }

    @Test
    fun aFailingProviderIsIsolatedAndListedLast() {
        val modules = ProviderRegistry.resolve(listOf(
            Candidate("broken_create") { error("boom") },
            Candidate("broken_identity") {
                object : UnifiedSettingsProvider by provider("broken_identity", 1) {
                    override val order: Int get() = error("bad order")
                }
            },
            Candidate("qol_unified") { provider("qol_unified", ModuleOrder.QOL) },
        ))
        assertEquals("qol_unified", modules.first().modId)
        assertTrue(modules.first().available)
        val failed = modules.drop(1)
        assertEquals(setOf("broken_create", "broken_identity"), failed.map { it.modId }.toSet())
        assertTrue(failed.none { it.available })
        assertTrue(failed.all { it.failure != null })
    }

    @Test
    fun aModuleIsListedOnce() {
        val modules = ProviderRegistry.resolve(listOf(
            Candidate("qol_unified") { provider("qol_unified", ModuleOrder.QOL) },
            Candidate("qol_unified") { provider("qol_unified", ModuleOrder.QOL) },
        ))
        assertEquals(1, modules.size)
    }

    @Test
    fun defaultsNeedNoOverride() {
        val p = provider("x", 1)
        assertTrue(p.categories().isEmpty())
        assertTrue(p.capabilities().isEmpty())
        assertNull(p.openSettings(null))
        assertFalse(ProviderRegistry.resolve(emptyList()).iterator().hasNext())
    }
}
