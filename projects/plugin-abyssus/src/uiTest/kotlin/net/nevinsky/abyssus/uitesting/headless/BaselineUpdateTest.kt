package net.nevinsky.abyssus.uitesting.headless

import net.nevinsky.abyssus.uitesting.screenshot.PendingBaselines
import net.nevinsky.abyssus.uitesting.screenshot.Pixels
import net.nevinsky.abyssus.uitesting.screenshot.ScreenshotArtifacts
import net.nevinsky.abyssus.uitesting.screenshot.ScreenshotMode
import net.nevinsky.abyssus.uitesting.screenshot.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BaselineUpdateTest {
    @get:Rule val tmp = TemporaryFolder()
    private val artifacts = ScreenshotArtifacts()

    private fun image(fill: Int) = Pixels(4, 4, IntArray(16) { fill })

    @Test
    fun updatesNeverRunOnCi() {
        val reason = UpdatePolicy(ci = true, selected = setOf("tree")).refusal("tree", emptyList())
        assertTrue(reason, reason!!.contains("CI"))
    }

    @Test
    fun updatesRefuseAMismatchedEnvironment() {
        val reason = UpdatePolicy(ci = false, selected = setOf("tree")).refusal("tree", listOf("jbr: expected 'a' but found 'b'"))
        assertTrue(reason, reason!!.contains("canonical environment") && reason.contains("jbr"))
    }

    @Test
    fun aCanonicalLocalUpdateIsAllowed() {
        assertNull(UpdatePolicy(ci = false, selected = setOf("tree")).refusal("tree", emptyList()))
    }

    @Test
    fun onlySelectedScenariosAreUpdated() {
        val policy = UpdatePolicy(ci = false, selected = setOf("a"))
        assertTrue(policy.selects("a"))
        assertFalse(policy.selects("b"))
    }

    @Test
    fun aFailingScenarioLeavesTheOldBaselineAlone() {
        val dir = tmp.root.toPath()
        artifacts.write(dir.resolve("a.png"), image(0xFF111111.toInt()))
        val pending = PendingBaselines(artifacts, dir)
        pending.add("a", image(0xFF999999.toInt()))
        pending.discard()
        assertEquals(0xFF111111.toInt(), artifacts.read(dir.resolve("a.png"))!![0, 0])
        assertTrue(pending.scenarios.isEmpty())
    }

    @Test
    fun aPassingScenarioWritesOnlyItsQueuedImages() {
        val dir = tmp.root.toPath()
        artifacts.write(dir.resolve("a.png"), image(0xFF111111.toInt()))
        artifacts.write(dir.resolve("b.png"), image(0xFF222222.toInt()))
        val pending = PendingBaselines(artifacts, dir)
        pending.add("a", image(0xFF999999.toInt()))
        pending.commit()
        assertEquals(0xFF999999.toInt(), artifacts.read(dir.resolve("a.png"))!![0, 0])
        assertEquals(0xFF222222.toInt(), artifacts.read(dir.resolve("b.png"))!![0, 0])
        assertNotNull(artifacts.read(dir.resolve("a.png")))
    }

    @Test
    fun modeParsing() {
        assertEquals(ScreenshotMode.COMPARE, ScreenshotMode.parse(null))
        assertEquals(ScreenshotMode.UPDATE, ScreenshotMode.parse("update"))
        assertEquals(ScreenshotMode.OFF, ScreenshotMode.parse("OFF"))
    }
}
