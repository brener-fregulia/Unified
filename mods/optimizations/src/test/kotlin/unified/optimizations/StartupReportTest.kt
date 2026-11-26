package unified.optimizations

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A Kotlin test of Kotlin code that calls Java. */
class StartupReportTest {
    @Test
    fun reportsTheModuleConfigurationDirectory() {
        assertEquals(
            "Unified: Optimizations initialized (no optimizations enabled yet). Configuration directory: " +
                Path.of("config", "unified_optimizations"),
            StartupReport.message(Path.of("config")),
        )
        assertEquals(true, StartupReport.message(Path.of("config"), 2).contains("(2 optimizations)"))
    }
}
