package net.nevinsky.abyssus.uitesting.screenshot

import com.intellij.remoterobot.fixtures.ComponentFixture
import net.nevinsky.abyssus.uitesting.IdeQueries
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Screenshot checks of one test class: the mode, the canonical-environment check and the pending baseline updates. */
class ScreenshotSuite(
    private val ide: IdeQueries,
    private val reportsDir: Path,
    private val baselineDir: Path = Paths.get(System.getProperty("abyssus.ui.baselines")),
    private val mode: ScreenshotMode = ScreenshotMode.parse(System.getProperty("abyssus.ui.screenshots")),
    private val policy: UpdatePolicy = UpdatePolicy(
        ci = System.getenv("CI")?.equals("true", ignoreCase = true) == true,
        selected = System.getProperty("abyssus.ui.screenshotScenarios", "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
    ),
    private val artifacts: ScreenshotArtifacts = ScreenshotArtifacts(),
) {
    private val pending = PendingBaselines(artifacts, baselineDir)
    private val comparator = ScreenshotComparator()

    private val manifest: ScreenshotEnvironment by lazy {
        ScreenshotEnvironment.parse(Files.readString(baselineDir.resolve("environment.properties")))
    }

    private val environmentDifferences: List<String> by lazy { manifest.differences(observedEnvironment()) }

    private fun observedEnvironment(): Map<String, String> {
        val packages = Paths.get("/etc/abyssus-ui-environment").takeIf { Files.exists(it) }
            ?.let { ScreenshotEnvironment.parseLines(Files.readString(it)) }.orEmpty()
        return ide.observeEnvironment() + packages.mapKeys { "pkg.${it.key}" }
    }

    /**
     * Captures [target] once its pixels stop changing, then compares with `<baselineDir>/<scenario>.png`, queues it for
     * update, or (mode off) only keeps it as a diagnostic. The caller has already waited for the content it expects.
     */
    fun check(scenario: String, target: ComponentFixture) {
        val pixels = captureStable(target)
        when (mode) {
            ScreenshotMode.OFF -> artifacts.write(reportsDir.resolve("screenshots").resolve(scenario).resolve("actual.png"), pixels)
            ScreenshotMode.COMPARE -> compare(scenario, pixels)
            ScreenshotMode.UPDATE ->
                if (policy.selects(scenario)) {
                    policy.refusal(scenario, environmentDifferences)?.let { throw AssertionError(it) }
                    pending.add(scenario, pixels)
                } else compare(scenario, pixels)
        }
    }

    private fun compare(scenario: String, pixels: Pixels) {
        if (environmentDifferences.isNotEmpty()) {
            throw AssertionError(
                "screenshots can only be compared in the canonical environment (docker/ui-tests); use -PuiScreenshots=off for " +
                    "interaction-only runs. Differences from ${baselineDir.resolve("environment.properties")}: ${environmentDifferences.joinToString("; ")}",
            )
        }
        val verdict = artifacts.check(scenario, baselineDir.resolve("$scenario.png"), pixels, reportsDir, manifest.describe())
        if (verdict is Verdict.Failed) throw AssertionError("${verdict.reason} (report: ${verdict.reportDir})")
    }

    /** Takes captures 400 ms apart until two in a row are identical, within 15 s. A moving image is a failure. */
    fun captureStable(target: ComponentFixture): Pixels {
        val deadline = System.nanoTime() + 15_000_000_000L
        var previous = capture(target)
        while (System.nanoTime() < deadline) {
            Thread.sleep(400)
            val next = capture(target)
            if (comparator.compare(previous, next) is Comparison.Identical) return next
            previous = next
        }
        throw AssertionError("the captured component never became visually stable")
    }

    private fun capture(target: ComponentFixture): Pixels {
        val image = target.getScreenshot()
        val argb = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, argb, 0, image.width)
        return Pixels(image.width, image.height, argb)
    }

    /** Called when the test passed: queued baselines are written. A failing scenario calls [discard] and keeps the old ones. */
    fun commit() = pending.commit()

    fun discard() = pending.discard()
}
