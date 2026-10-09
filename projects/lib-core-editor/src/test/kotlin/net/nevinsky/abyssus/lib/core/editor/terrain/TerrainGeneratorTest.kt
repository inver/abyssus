/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException

class TerrainGeneratorTest {
    private val generator = TerrainGenerator(FastNoiseSamplerFactory())
    private val settings = TerrainGenerationSettings()

    private fun digest(heights: FloatArray): String {
        val buffer = ByteBuffer.allocate(heights.size * 4)
        heights.forEach(buffer::putFloat)
        return MessageDigest.getInstance("SHA-256").digest(buffer.array()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `golden heights for the default settings`() {
        val heights = generator.generate(64, 1600, settings)
        assertEquals(GOLDEN_64, digest(heights))
    }

    @Test
    fun `equal settings give equal heights`() {
        assertArrayEquals(generator.generate(40, 800, settings), generator.generate(40, 800, settings), 0f)
    }

    @Test
    fun `a different seed gives different heights`() {
        val a = generator.generate(40, 800, settings)
        val b = generator.generate(40, 800, settings.copy(seed = 54321))
        assertNotEquals(digest(a), digest(b))
    }

    @Test
    fun `heights are finite and inside the range`() {
        val s = settings.copy(minHeight = -30f, maxHeight = 45f, octaves = 8)
        val heights = generator.generate(90, 1600, s)
        assertTrue(heights.all { it.isFinite() && it >= -30f && it <= 45f })
        assertTrue("noise should use most of the range", heights.max() - heights.min() > 40f)
    }

    @Test
    fun `zero persistence is the first octave alone`() {
        val flat = generator.generate(30, 600, settings.copy(persistence = 0f, octaves = 8))
        val one = generator.generate(30, 600, settings.copy(persistence = 0.5f, octaves = 1))
        assertArrayEquals(one, flat, 1e-3f)
    }

    @Test
    fun `heights depend on world position not resolution`() {
        val coarse = generator.generate(11, 1000, settings)
        val fine = generator.generate(21, 1000, settings)
        for (z in 0 until 11) for (x in 0 until 11) {
            assertEquals(coarse[z * 11 + x], fine[(z * 2) * 21 + x * 2], 1e-3f)
        }
    }

    @Test
    fun `resolution endpoints 2 and 255 are accepted`() {
        assertEquals(4, generator.generate(2, 10, settings).size)
        assertEquals(255 * 255, generator.generate(255, 1600, settings).size)
    }

    @Test
    fun `resolution outside 2 to 255 and a non positive size are refused`() {
        assertThrows(IllegalArgumentException::class.java) { generator.generate(1, 100, settings) }
        assertThrows(IllegalArgumentException::class.java) { generator.generate(256, 100, settings) }
        assertThrows(IllegalArgumentException::class.java) { generator.generate(10, 0, settings) }
    }

    @Test
    fun `z major layout puts a changing noise along z in rows`() {
        val calls = mutableListOf<Pair<Float, Float>>()
        val g = TerrainGenerator { _, _, _, _, _ -> NoiseSampler { x, z -> calls += x to z; 0f } }
        g.generate(3, 20, settings)
        assertEquals(listOf(0f to 0f, 10f to 0f, 20f to 0f, 0f to 10f), calls.take(4))
    }

    @Test
    fun `invalid settings are listed`() {
        assertTrue(settings.valid)
        assertEquals(listOf(SettingsError.FEATURE_SIZE), settings.copy(featureSize = 0f).errors())
        assertEquals(listOf(SettingsError.FEATURE_SIZE), settings.copy(featureSize = Float.NaN).errors())
        assertEquals(listOf(SettingsError.HEIGHT_NOT_FINITE), settings.copy(minHeight = Float.NEGATIVE_INFINITY).errors())
        assertEquals(listOf(SettingsError.HEIGHT_NOT_FINITE), settings.copy(maxHeight = Float.NaN).errors())
        assertEquals(listOf(SettingsError.HEIGHT_RANGE), settings.copy(minHeight = 5f, maxHeight = 5f).errors())
        assertEquals(listOf(SettingsError.HEIGHT_RANGE), settings.copy(minHeight = 9f, maxHeight = 1f).errors())
        assertEquals(listOf(SettingsError.OCTAVES), settings.copy(octaves = 0).errors())
        assertEquals(listOf(SettingsError.OCTAVES), settings.copy(octaves = 9).errors())
        assertEquals(listOf(SettingsError.PERSISTENCE), settings.copy(persistence = -0.1f).errors())
        assertEquals(listOf(SettingsError.PERSISTENCE), settings.copy(persistence = 1.1f).errors())
        assertEquals(listOf(SettingsError.LACUNARITY), settings.copy(lacunarity = 0.9f).errors())
        assertEquals(listOf(SettingsError.LACUNARITY), settings.copy(lacunarity = 4.1f).errors())
        assertTrue(settings.copy(octaves = 8, persistence = 1f, lacunarity = 4f).valid)
        assertTrue(settings.copy(octaves = 1, persistence = 0f, lacunarity = 1f).valid)
        assertFalse(settings.copy(octaves = 0, featureSize = -1f).valid)
        assertThrows(IllegalArgumentException::class.java) { generator.generate(10, 100, settings.copy(featureSize = 0f)) }
    }

    @Test
    fun `cancellation stops generation`() {
        var rows = 0
        assertThrows(CancellationException::class.java) {
            generator.generate(50, 100, settings) { if (++rows == 3) throw CancellationException("stop") }
        }
        assertEquals(3, rows)
    }

    private companion object {
        const val GOLDEN_64 = "4e40999a89c73223302bd27191b986f50a30526de8a1e09db6714e1a593e3337"
    }
}
