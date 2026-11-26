package unified.optimizations.core

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class OptimizationsConfigTest {
    @TempDir
    lateinit var dir: Path

    private val file get() = dir.resolve("unified_optimizations").resolve("config.json")

    @Test
    fun missingFileUsesDefaultsAndWritesNothing() {
        val state = Optimizations.startForTest(file)
        assertEquals(OptimizationsConfig.LoadStatus.MISSING, state.status)
        assertTrue(Optimizations.isActive("fast_ip_ping"), "Fast IP Ping on by default")
        assertFalse(Optimizations.isActive("fast_quit"), "FastQuit off by default")
        assertFalse(Files.exists(file))
    }

    @Test
    fun choicesPersistIndependentlyAndApplyOnlyAfterSaving() {
        Optimizations.startForTest(file)
        assertNull(Optimizations.setEnabled("fast_quit", true))
        assertTrue(Optimizations.isActive("fast_quit"))
        assertTrue(Optimizations.isActive("fast_ip_ping"), "the other feature is untouched")
        Optimizations.startForTest(file)
        assertTrue(Optimizations.isEnabled("fast_quit"), "reloaded from disk")
        val json = Files.readString(file)
        assertTrue(json.contains("\"schemaVersion\": 1") && json.contains("\"fast_quit\""))
        assertFalse(json.contains("fast_ip_ping"), "only explicit choices are stored")
        assertTrue(Files.list(file.parent).use { s -> s.noneMatch { it.fileName.toString().endsWith(".tmp") } })
    }

    @Test
    fun unknownContentIsPreserved() {
        Files.createDirectories(file.parent)
        Files.writeString(file, """{"schemaVersion":1,"custom":{"a":1},"features":{"future_feature":{"enabled":true,"x":2}}}""")
        Optimizations.startForTest(file)
        assertNull(Optimizations.setEnabled("fast_ip_ping", false))
        val json = Files.readString(file)
        assertTrue(json.contains("\"custom\"") && json.contains("future_feature") && json.contains("\"x\": 2"))
    }

    @Test
    fun invalidValuesFallBackToDefaults() {
        Files.createDirectories(file.parent)
        Files.writeString(file, """{"schemaVersion":1,"features":{"fast_ip_ping":{"enabled":"yes"},"fast_quit":3}}""")
        Optimizations.startForTest(file)
        assertTrue(Optimizations.isEnabled("fast_ip_ping"))
        assertFalse(Optimizations.isEnabled("fast_quit"))
    }

    @Test
    fun anUnreadableFileIsKeptAsideNotSilentlyOverwritten() {
        Files.createDirectories(file.parent)
        Files.writeString(file, "{ not json")
        val config = Optimizations.startForTest(file)
        assertEquals(OptimizationsConfig.LoadStatus.INVALID, config.status)
        assertEquals("{ not json", Files.readString(file), "untouched until the player changes a setting")
        assertNull(Optimizations.setEnabled("fast_quit", true))
        assertEquals("{ not json", Files.readString(file.resolveSibling("config.json.invalid")))
        assertTrue(Files.readString(file).contains("fast_quit"))
    }

    @Test
    fun aNewerFileIsNeverRewrittenAndTheChangeIsNotApplied() {
        Files.createDirectories(file.parent)
        Files.writeString(file, """{"schemaVersion":99}""")
        Optimizations.startForTest(file)
        assertNotNull(Optimizations.setEnabled("fast_quit", true))
        assertFalse(Optimizations.isEnabled("fast_quit"), "a failed save is not applied")
        assertEquals("""{"schemaVersion":99}""", Files.readString(file))
    }

    @Test
    fun aConflictingModKeepsTheFeatureInactiveButRemembersTheChoice() {
        Optimizations.startForTest(file, setOf("fastipping"))
        assertTrue(Optimizations.isEnabled("fast_ip_ping"))
        assertFalse(Optimizations.isActive("fast_ip_ping"))
        assertEquals(listOf("fastipping"), Optimizations.conflictsOf("fast_ip_ping"))
        Optimizations.startForTest(file, setOf("fastquit"))
        Optimizations.setEnabled("fast_quit", true)
        assertFalse(Optimizations.isActive("fast_quit"), "FastQuit (the original) does the same job")
    }
}
