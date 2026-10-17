/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.databind.SerializationFeature
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** How far two renders of the same scene are apart. */
data class PixelDiff(
    /** The largest difference of any one colour channel, 0..255. */
    val maxChannel: Int,
    /** The share (0..1) of pixels where any channel differs at all. */
    val differingShare: Double,
    /** The share of pixels where any channel differs by more than [PixelEvidence.NOTICEABLE]. */
    val noticeableShare: Double,
)

/** The tolerance one case was recorded with: what repeated renders on the same device differ by, never widened. */
data class PixelTolerance(val case: String, val runs: Int, val width: Int, val height: Int, val tolerance: PixelDiff)

/** Pixel comparison of renders and the recorded tolerance files of the graphics evidence. */
class PixelEvidence {
    fun diff(a: BufferedImage, b: BufferedImage): PixelDiff {
        require(a.width == b.width && a.height == b.height) { "images differ in size: ${a.width}x${a.height} vs ${b.width}x${b.height}" }
        var max = 0
        var differing = 0L
        var noticeable = 0L
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getRGB(x, y)
            val q = b.getRGB(x, y)
            if (p == q) continue
            val d = maxOf(
                Math.abs(((p shr 16) and 255) - ((q shr 16) and 255)),
                Math.abs(((p shr 8) and 255) - ((q shr 8) and 255)),
                Math.abs((p and 255) - (q and 255)),
            )
            if (d == 0) continue
            differing++
            if (d > NOTICEABLE) noticeable++
            if (d > max) max = d
        }
        val total = a.width.toDouble() * a.height
        return PixelDiff(max, differing / total, noticeable / total)
    }

    /** The loosest of [diffs]: the tolerance repeated runs are held to. */
    fun toleranceOf(diffs: List<PixelDiff>) = PixelDiff(
        diffs.maxOf { it.maxChannel }, diffs.maxOf { it.differingShare }, diffs.maxOf { it.noticeableShare },
    )

    /** True when [actual] is no further from the reference than the recorded [tolerance]. */
    fun within(actual: PixelDiff, tolerance: PixelDiff) =
        actual.maxChannel <= tolerance.maxChannel && actual.differingShare <= tolerance.differingShare &&
            actual.noticeableShare <= tolerance.noticeableShare

    fun writeImage(image: BufferedImage, file: File) {
        file.absoluteFile.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    fun readImage(file: File): BufferedImage = requireNotNull(ImageIO.read(file)) { "not an image: $file" }

    private val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    fun writeTolerance(tolerance: PixelTolerance, file: File) {
        file.absoluteFile.parentFile.mkdirs()
        file.writeText(mapper.writeValueAsString(tolerance) + "\n")
    }

    fun readTolerance(file: File): PixelTolerance = mapper.readValue(file)

    companion object {
        /** A channel difference above this is visible to the eye on a flat colour; below it is dithering and rounding. */
        const val NOTICEABLE = 2
    }
}
