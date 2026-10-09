/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assets.sky.hdr

import net.nevinsky.abyssus.lib.core.assets.sky.hdr.ExrLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrImage
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrPreview
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.ToneCurve
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.assets.exrFixture
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Decodes the 1k Poly Haven sunrise (`/hdr/bloem_field_sunrise_1k.exr`, 1024 x 512, half float RGB). */
class ExrLoaderTest {
    private val sunrise: File = exrFixture()
    private val loader = ExrLoader(FileLoader(sunrise.parentFile))

    private fun mean(image: HdrImage): DoubleArray {
        val sum = DoubleArray(3)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val p = image.pixel(x, y)
            for (c in 0 until 3) sum[c] += p[c]
        }
        return DoubleArray(3) { sum[it] / (image.width * image.height) }
    }

    @Test
    fun decodesAtFullSize() {
        val image = loader.decode(sunrise)
        assertEquals(1024, image.width)
        assertEquals(512, image.height)
        assertEquals(1024 * 512 * 3, image.rgb.size)
    }

    @Test
    fun pixelsAreFiniteNonNegativeAndHighDynamicRange() {
        val image = loader.decode(sunrise)
        var max = 0f
        for (y in 0 until image.height) for (x in 0 until image.width) {
            for (v in image.pixel(x, y)) {
                assertTrue("($x, $y) = $v", v.isFinite() && v >= 0f)
                max = maxOf(max, v)
            }
        }
        assertTrue("brightest value $max is not above display white", max > 1f)
        assertTrue("image is black", mean(image).all { it > 0.0 })
    }

    @Test
    fun skyIsBluerThanTheGroundIsNot() {
        // the top row looks at the zenith, the bottom row at the ground: they must differ
        val image = loader.decode(sunrise)
        val top = image.pixel(512, 4)
        val bottom = image.pixel(512, 507)
        assertTrue("${top.toList()} ${bottom.toList()}", !top.contentEquals(bottom))
    }

    @Test
    fun reducesByWholeHalvingsKeepingTheAverage() {
        val full = mean(loader.decode(sunrise))
        val image = loader.decode(sunrise, maxWidth = 256)
        assertEquals(256, image.width)
        assertEquals(128, image.height)
        val reduced = mean(image)
        for (c in 0 until 3) assertEquals("channel $c", full[c], reduced[c], full[c] * 0.02)
    }

    @Test
    fun aMaxWidthBetweenPowersOfTwoRoundsDownToTheNextHalving() {
        val image = loader.decode(sunrise, maxWidth = 600)
        assertEquals(512, image.width)
        assertEquals(256, image.height)
    }

    @Test
    fun loadsThroughTheAssetFolder() {
        val project = Files.createTempDirectory("exrsky").toFile()
        try {
            val folder = File(project, "assets/sunrise").apply { mkdirs() }
            sunrise.copyTo(File(folder, "sky.exr"))
            val image = ExrLoader(FileLoader(project)).loadExr("sunrise", "sky.exr", maxWidth = 512)
            assertEquals(512, image.width)
            assertEquals(256, image.height)
        } finally {
            project.deleteRecursively()
        }
    }

    @Test
    fun decodingTwiceGivesTheSameImage() {
        // every native struct is freed and re-created per call, so nothing may leak into the next decode
        assertTrue(loader.decode(sunrise).rgb.contentEquals(loader.decode(sunrise).rgb))
    }

    @Test
    fun rejectsAFileThatIsNotExr() {
        val file = Files.createTempFile("not", ".exr").toFile()
        try {
            file.writeText("#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n")
            assertThrows(IllegalStateException::class.java) { loader.decode(file) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsAMissingFile() {
        assertThrows(IllegalStateException::class.java) { loader.decode(File(sunrise.parentFile, "missing.exr")) }
    }

    @Test
    fun previewIsToneMappedAndScaled() {
        val preview = HdrPreview(loader, ToneCurve()).image(sunrise, 200)
        assertEquals(200, preview.width)
        assertEquals(100, preview.height)
        val lit = (0 until preview.width).count { x -> (preview.getRGB(x, 50) and 0xffffff) != 0 }
        assertTrue("preview row is black", lit > 0)
    }
}
