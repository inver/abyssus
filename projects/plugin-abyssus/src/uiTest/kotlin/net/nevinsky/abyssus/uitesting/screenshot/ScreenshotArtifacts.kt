package net.nevinsky.abyssus.uitesting.screenshot

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

sealed interface Verdict {
    data object Match : Verdict
    data class Failed(val reason: String, val reportDir: Path?) : Verdict
}

/** PNG reading and writing around the pure comparator, plus the expected/actual/diff report of a failed comparison. */
class ScreenshotArtifacts(private val comparator: ScreenshotComparator = ScreenshotComparator()) {

    /** Decoded pixels of a PNG, or null when it is missing or not a readable image. */
    fun read(file: Path): Pixels? {
        if (!Files.isRegularFile(file)) return null
        val image = runCatching { Files.newInputStream(file).use(ImageIO::read) }.getOrNull() ?: return null
        return image.toPixels()
    }

    fun write(file: Path, pixels: Pixels) {
        Files.createDirectories(file.parent)
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, pixels.width, pixels.height, pixels.argb, 0, pixels.width)
        check(ImageIO.write(image, "png", file.toFile())) { "no PNG writer for $file" }
    }

    /**
     * Compares [actual] with the baseline [baseline]. On any failure writes `actual.png`, `expected.png` (when readable),
     * `diff.png` (when sizes agree) and `report.txt` under `<reportsDir>/screenshots/<scenario>`.
     */
    fun check(scenario: String, baseline: Path, actual: Pixels, reportsDir: Path, environment: String = ""): Verdict {
        val expected = read(baseline)
        val outcome: Comparison? = expected?.let { comparator.compare(it, actual) }
        if (outcome is Comparison.Identical) return Verdict.Match

        val reason = when {
            !Files.exists(baseline) -> "missing baseline $baseline"
            expected == null -> "unreadable baseline $baseline"
            outcome is Comparison.SizeMismatch ->
                "size mismatch for $scenario: expected ${outcome.expectedWidth}x${outcome.expectedHeight}, actual ${outcome.actualWidth}x${outcome.actualHeight}"
            outcome is Comparison.Changed -> {
                val b = outcome.bounds
                "$scenario differs in ${outcome.changedPixels} pixels, bounds x=${b.minX}..${b.maxX} y=${b.minY}..${b.maxY} (${b.width}x${b.height})"
            }
            else -> error("unreachable")
        }
        val dir = reportsDir.resolve("screenshots").resolve(scenario)
        Files.createDirectories(dir)
        write(dir.resolve("actual.png"), actual)
        if (expected != null) write(dir.resolve("expected.png"), expected)
        if (outcome is Comparison.Changed) write(dir.resolve("diff.png"), outcome.diff)
        Files.writeString(dir.resolve("report.txt"), "$reason\nbaseline: $baseline\n$environment\n")
        return Verdict.Failed(reason, dir)
    }

    private fun BufferedImage.toPixels(): Pixels {
        val argb = IntArray(width * height)
        getRGB(0, 0, width, height, argb, 0, width)
        return Pixels(width, height, argb)
    }
}
