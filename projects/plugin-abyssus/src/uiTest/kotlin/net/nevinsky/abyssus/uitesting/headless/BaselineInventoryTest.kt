package net.nevinsky.abyssus.uitesting.headless

import net.nevinsky.abyssus.uitesting.screenshot.SCREENSHOT_SCENARIOS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class BaselineInventoryTest {
    private val dir get() = Paths.get(System.getProperty("abyssus.ui.baselines"))

    private fun committed(dir: Path): Set<String> = Files.list(dir).use { list ->
        list.map { it.fileName.toString() }.filter { it.endsWith(".png") }.map { it.removeSuffix(".png") }.toList().toSet()
    }

    @Test
    fun everyInventoryScenarioHasABaselineAndNothingElseDoes() {
        val baselines = committed(dir)
        val missing = SCREENSHOT_SCENARIOS - baselines
        val orphaned = baselines - SCREENSHOT_SCENARIOS
        assertTrue("missing baselines: $missing", missing.isEmpty())
        assertTrue("orphaned baselines (not in the inventory): $orphaned", orphaned.isEmpty())
    }

    @Test
    fun aMissingOrOrphanedFileIsReportedByName() {
        val tmp = Files.createTempDirectory("baselines")
        try {
            SCREENSHOT_SCENARIOS.drop(1).forEach { Files.write(tmp.resolve("$it.png"), byteArrayOf(1)) }
            Files.write(tmp.resolve("stray.png"), byteArrayOf(1))
            val baselines = committed(tmp)
            assertEquals(setOf(SCREENSHOT_SCENARIOS.first()), SCREENSHOT_SCENARIOS - baselines)
            assertEquals(setOf("stray"), baselines - SCREENSHOT_SCENARIOS)
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }

    @Test
    fun theInventoryHasNoDuplicatesAndSeventeenScenarios() {
        assertEquals(17, SCREENSHOT_SCENARIOS.size)
    }
}
