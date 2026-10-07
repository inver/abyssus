/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CloudFieldTest {
    private val field = CloudField()
    private val cumulus = CloudBand(CloudLevel.LOW, CloudType.CUMULUS, windX = 10f, windZ = -4f)
    private val points = listOf(0f to 0f, 1234.5f to -987.25f, -40_000f to 25_000f, 77_777f to 3f)
    private val times = listOf(0.0, 12.5, 3600.0)

    /** Golden values, so a change to the field (which the GLSL twin must follow) is a deliberate one. */
    private val golden = floatArrayOf(0f, 1f, 0f, 0.7518357f, 0f, 1f, 0f, 0.61773926f, 0f, 0.28672636f, 0.93340844f, 0f)
    private val goldenFbm = floatArrayOf(
        0.5638745f, 0.44648227f, 0.6102617f, 0.7121591f, 0.4427094f, 0.2864337f, 0.4752677f, 0.49748886f,
        0.53421235f, 0.5381302f, 0.60638356f, 0.746299f, 0.3471419f, 0.38986513f, 0.8017738f,
    )

    private fun sampleFbm(): FloatArray = listOf(0.1f to 0.2f, 3.7f to -12.25f, 63.9f to 0.05f, -100.3f to 7.77f, 1000.5f to 1000.5f)
        .flatMap { (u, v) -> listOf(field.fbm(u, v, 0, 4), field.fbm(u, v, 5, 5), field.noise(u, v, 3, CLOUD_NOISE_PERIOD)) }
        .toFloatArray()

    private fun sample(): FloatArray = times.flatMap { t ->
        val offset = field.windOffset(cumulus, t)
        points.map { (x, z) -> field.coverage(cumulus, x, z, offset) }
    }.toFloatArray()

    @Test
    fun goldenValuesAtFixedPointsAndTimes() {
        assertArrayEquals(sample().joinToString(), golden, sample(), 1e-5f)
        assertArrayEquals(sampleFbm().joinToString(), goldenFbm, sampleFbm(), 1e-5f)
    }

    @Test
    fun outputIsDeterministic() {
        assertArrayEquals(sample(), CloudField().let { other ->
            times.flatMap { t ->
                val offset = other.windOffset(cumulus, t)
                points.map { (x, z) -> other.coverage(cumulus, x, z, offset) }
            }.toFloatArray()
        }, 0f)
    }

    @Test
    fun cloudsMoveWithTheWind() {
        val t = 30.0
        val moved = field.windOffset(cumulus, t)
        val still = field.windOffset(cumulus, 0.0)
        for ((x, z) in points) {
            val later = field.coverage(cumulus, x + cumulus.windX * t.toFloat(), z + cumulus.windZ * t.toFloat(), moved)
            assertEquals("the cloud at ($x, $z) is downwind after $t s", field.coverage(cumulus, x, z, still), later, 2e-3f)
        }
    }

    @Test
    fun driftIsContinuousAcrossTheWrap() {
        // a day of drift: the offset wraps many times but neighbouring frames still sample neighbouring noise
        var previous = field.coverage(cumulus, 500f, 500f, field.windOffset(cumulus, 86_400.0))
        for (frame in 1..120) {
            val value = field.coverage(cumulus, 500f, 500f, field.windOffset(cumulus, 86_400.0 + frame / 60.0))
            assertTrue("frame $frame jumped from $previous to $value", abs(value - previous) < 0.05f)
            previous = value
        }
        for (t in listOf(0.0, 1e5, 1e7, 1e9)) {
            field.windOffset(cumulus, t).forEach { assertTrue("$t: $it", it >= 0f && it < CLOUD_NOISE_PERIOD) }
        }
    }

    @Test
    fun coverageZeroIsEmptyAndOneIsFull() {
        val clear = cumulus.copy(coverage = 0f)
        val full = cumulus.copy(coverage = 1f)
        val offset = field.windOffset(cumulus, 7.0)
        for (x in -50..50) for (z in -50..50) {
            assertEquals(0f, field.coverage(clear, x * 731f, z * 517f, offset))
            assertEquals(1f, field.coverage(full, x * 731f, z * 517f, offset))
        }
    }

    @Test
    fun moreCoverageCoversMoreOfTheSky() {
        fun share(coverage: Float): Double {
            val band = cumulus.copy(coverage = coverage)
            val offset = field.windOffset(band, 0.0)
            var sum = 0.0
            for (x in 0 until 100) for (z in 0 until 100) sum += field.coverage(band, x * 977f, z * 1009f, offset)
            return sum / 10_000
        }
        val shares = listOf(0.2f, 0.4f, 0.6f, 0.8f).map(::share)
        assertEquals(shares.sorted(), shares)
        assertTrue("0.4 is scattered, not overcast: $shares", shares[1] in 0.1..0.7)
    }

    @Test
    fun noiseRepeatsEveryPeriod() {
        for (seed in listOf(0, 17, 99)) for (i in 0 until 50) {
            val u = i * 1.37f
            val v = i * -0.73f
            assertEquals(field.noise(u, v, seed, 8f), field.noise(u + 8f, v - 16f, seed, 8f), 1e-4f)
        }
    }

    @Test
    fun shapeIsZeroOutsideTheBand() {
        for (type in CloudType.entries) {
            assertEquals(0f, field.shape(type, 1f, -0.01f))
            assertEquals(0f, field.shape(type, 1f, 1.01f))
            assertTrue("$type has density mid-band", field.shape(type, 1f, 0.5f) > 0.3f)
            assertEquals(0f, field.shape(type, 0f, 0.5f))
        }
    }
}
