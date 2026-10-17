package net.nevinsky.abyssus.uitesting.headless

import net.nevinsky.abyssus.uitesting.screenshot.Bounds
import net.nevinsky.abyssus.uitesting.screenshot.Comparison
import net.nevinsky.abyssus.uitesting.screenshot.Pixels
import net.nevinsky.abyssus.uitesting.screenshot.ScreenshotArtifacts
import net.nevinsky.abyssus.uitesting.screenshot.ScreenshotComparator
import net.nevinsky.abyssus.uitesting.screenshot.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class ScreenshotComparatorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun image(w: Int, h: Int, fill: Int = 0xFF204060.toInt()) = Pixels(w, h, IntArray(w * h) { fill })

    private fun Pixels.with(x: Int, y: Int, argb: Int) = Pixels(width, height, argb.let { v -> this.argb.copyOf().also { it[y * width + x] = v } })

    @Test
    fun identicalPixelsPass() {
        assertEquals(Comparison.Identical, ScreenshotComparator().compare(image(8, 6), image(8, 6)))
    }

    @Test
    fun oneChangedPixelFailsWithCountBoundsAndDiff() {
        val result = ScreenshotComparator().compare(image(8, 6), image(8, 6).with(3, 2, 0xFF204061.toInt()))
        result as Comparison.Changed
        assertEquals(1, result.changedPixels)
        assertEquals(Bounds(3, 2, 3, 2), result.bounds)
        assertEquals(0xFFFF0000.toInt(), result.diff[3, 2])
        assertTrue("unchanged pixels are dimmed, not red", result.diff[0, 0] != 0xFFFF0000.toInt())
    }

    @Test
    fun boundsCoverEveryChangedPixel() {
        val actual = image(10, 10).with(1, 8, 0).with(7, 2, 0)
        val result = ScreenshotComparator().compare(image(10, 10), actual) as Comparison.Changed
        assertEquals(2, result.changedPixels)
        assertEquals(Bounds(1, 2, 7, 8), result.bounds)
    }

    @Test
    fun alphaDifferenceCounts() {
        val result = ScreenshotComparator().compare(image(2, 2), image(2, 2).with(0, 0, 0x80204060.toInt()))
        assertTrue(result is Comparison.Changed)
    }

    @Test
    fun sizeMismatchFails() {
        assertEquals(Comparison.SizeMismatch(8, 6, 8, 7), ScreenshotComparator().compare(image(8, 6), image(8, 7)))
    }

    @Test
    fun matchingImagesWriteNoReport() {
        val artifacts = ScreenshotArtifacts()
        val baseline = tmp.root.toPath().resolve("baseline.png")
        artifacts.write(baseline, image(8, 6))
        val verdict = artifacts.check("same", baseline, image(8, 6), tmp.root.toPath().resolve("reports"))
        assertEquals(Verdict.Match, verdict)
        assertTrue(!Files.exists(tmp.root.toPath().resolve("reports")))
    }

    @Test
    fun changedPixelWritesReadableExpectedActualAndDiffPngs() {
        val artifacts = ScreenshotArtifacts()
        val baseline = tmp.root.toPath().resolve("baseline.png")
        artifacts.write(baseline, image(8, 6))
        val verdict = artifacts.check("changed", baseline, image(8, 6).with(4, 1, 0xFF000000.toInt()), tmp.root.toPath().resolve("reports"), "env: test")
        verdict as Verdict.Failed
        assertTrue(verdict.reason, "1 pixels" in verdict.reason && "x=4..4 y=1..1" in verdict.reason)
        for (name in listOf("actual.png", "expected.png", "diff.png")) {
            val pixels = artifacts.read(verdict.reportDir!!.resolve(name))
            assertNotNull("$name is a readable PNG", pixels)
            assertEquals(8, pixels!!.width)
        }
        assertTrue("env: test" in Files.readString(verdict.reportDir!!.resolve("report.txt")))
    }

    @Test
    fun sizeMismatchStillWritesActualAndExpected() {
        val artifacts = ScreenshotArtifacts()
        val baseline = tmp.root.toPath().resolve("baseline.png")
        artifacts.write(baseline, image(8, 6))
        val verdict = artifacts.check("size", baseline, image(9, 6), tmp.root.toPath().resolve("reports")) as Verdict.Failed
        assertTrue(verdict.reason, "size mismatch" in verdict.reason)
        assertTrue(Files.exists(verdict.reportDir!!.resolve("actual.png")))
        assertTrue(Files.exists(verdict.reportDir.resolve("expected.png")))
        assertTrue(!Files.exists(verdict.reportDir.resolve("diff.png")))
    }

    @Test
    fun missingBaselineFailsAndKeepsTheActualCapture() {
        val verdict = ScreenshotArtifacts().check("absent", tmp.root.toPath().resolve("nope.png"), image(4, 4), tmp.root.toPath().resolve("reports")) as Verdict.Failed
        assertTrue(verdict.reason, "missing baseline" in verdict.reason)
        assertTrue(Files.exists(verdict.reportDir!!.resolve("actual.png")))
    }

    @Test
    fun unreadableBaselineFails() {
        val baseline = tmp.root.toPath().resolve("broken.png")
        Files.writeString(baseline, "not a png")
        val verdict = ScreenshotArtifacts().check("broken", baseline, image(4, 4), tmp.root.toPath().resolve("reports")) as Verdict.Failed
        assertTrue(verdict.reason, "unreadable baseline" in verdict.reason)
    }
}
