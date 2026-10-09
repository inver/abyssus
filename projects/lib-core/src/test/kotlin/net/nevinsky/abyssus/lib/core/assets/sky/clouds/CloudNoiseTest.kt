/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assets.sky.clouds

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CloudNoiseTest {
    private val noise = CloudNoiseGenerator().generate()

    private fun at(volume: ByteArray, size: Int, x: Int, y: Int, z: Int) = volume[(z * size + y) * size + x].toInt() and 255

    @Test
    fun sizesAndFullRange() {
        assertEquals(CLOUD_BASE_NOISE_SIZE * CLOUD_BASE_NOISE_SIZE * CLOUD_BASE_NOISE_SIZE, noise.base.size)
        assertEquals(CLOUD_DETAIL_NOISE_SIZE * CLOUD_DETAIL_NOISE_SIZE * CLOUD_DETAIL_NOISE_SIZE, noise.detail.size)
        for (volume in listOf(noise.base, noise.detail)) {
            val values = volume.map { it.toInt() and 255 }
            assertEquals(0, values.min())
            assertEquals(255, values.max())
        }
    }

    @Test
    fun volumesTileAcrossEveryFace() {
        // the step across a face (last texel to the first) is no bigger than the steps inside the volume
        for ((volume, size) in listOf(noise.base to noise.baseSize, noise.detail to noise.detailSize)) {
            var inside = 0.0
            var across = 0.0
            var n = 0
            for (a in 0 until size step 3) for (b in 0 until size step 3) {
                inside += abs(at(volume, size, size / 2, a, b) - at(volume, size, size / 2 + 1, a, b))
                across += abs(at(volume, size, size - 1, a, b) - at(volume, size, 0, a, b)) +
                    abs(at(volume, size, a, size - 1, b) - at(volume, size, a, 0, b)) +
                    abs(at(volume, size, a, b, size - 1) - at(volume, size, a, b, 0))
                n++
            }
            assertTrue("seams ${across / (3 * n)} vs inside ${inside / n}", across / (3 * n) <= inside / n * 1.5 + 1)
        }
    }

    @Test
    fun deterministic() {
        val again = CloudNoiseGenerator().generate()
        assertArrayEquals(noise.base, again.base)
        assertArrayEquals(noise.detail, again.detail)
    }
}
