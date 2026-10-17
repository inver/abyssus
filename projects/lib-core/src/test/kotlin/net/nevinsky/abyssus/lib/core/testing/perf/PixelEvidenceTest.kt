/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

import org.junit.Assert.*
import org.junit.Test
import java.awt.image.BufferedImage
import java.nio.file.Files

class PixelEvidenceTest {
    private val evidence = PixelEvidence()

    private fun image(color: Int) = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).also { for (y in 0 until 10) for (x in 0 until 10) it.setRGB(x, y, color) }

    @Test
    fun identicalImagesDifferByNothing() = assertEquals(PixelDiff(0, 0.0, 0.0), evidence.diff(image(0x102030), image(0x102030)))

    @Test
    fun theLargestChannelAndTheShareOfDifferingPixelsAreReported() {
        val b = image(0x102030).also { it.setRGB(0, 0, 0x102034); it.setRGB(1, 0, 0x102030 + 0x050000) }
        val diff = evidence.diff(image(0x102030), b)
        assertEquals(5, diff.maxChannel)
        assertEquals(0.02, diff.differingShare, 1e-9)
        assertEquals(0.02, diff.noticeableShare, 1e-9) // steps of 4 and 5 are both above the noticeable limit of 2
    }

    @Test
    fun theToleranceIsTheLoosestOfTheRepeatedRunsAndDoesNotWiden() {
        val tolerance = evidence.toleranceOf(listOf(PixelDiff(1, 0.001, 0.0), PixelDiff(3, 0.0005, 0.0002)))
        assertEquals(PixelDiff(3, 0.001, 0.0002), tolerance)
        assertTrue(evidence.within(PixelDiff(3, 0.001, 0.0002), tolerance))
        assertFalse(evidence.within(PixelDiff(4, 0.0, 0.0), tolerance))
    }

    @Test
    fun differentSizesAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { evidence.diff(image(0), BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB)) }
    }

    @Test
    fun aToleranceFileAndAnImageRoundTrip() {
        val dir = Files.createTempDirectory("pixels").toFile()
        try {
            val tolerance = PixelTolerance("untitled", 5, 10, 10, PixelDiff(2, 0.01, 0.0))
            evidence.writeTolerance(tolerance, dir.resolve("t/untitled.json"))
            assertEquals(tolerance, evidence.readTolerance(dir.resolve("t/untitled.json")))
            evidence.writeImage(image(0x336699), dir.resolve("i/a.png"))
            assertEquals(PixelDiff(0, 0.0, 0.0), evidence.diff(image(0x336699), evidence.readImage(dir.resolve("i/a.png"))))
        } finally {
            dir.deleteRecursively()
        }
    }
}
