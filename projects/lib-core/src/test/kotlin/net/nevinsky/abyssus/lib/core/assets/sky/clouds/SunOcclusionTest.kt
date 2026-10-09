/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudLevel
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.SUN_OCCLUSION_FLOOR
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.SunOcclusion
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.bandDistance
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.AtmosphereParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunOcclusionTest {
    private val radius = AtmosphereParams().planetRadius
    private val sun = Vector3(0.3f, 0.8f, 0.2f).nor()
    private val occlusion = SunOcclusion()

    private fun clouds(vararg bands: CloudBand) = CloudMeta(low = bands.firstOrNull { it.level == CloudLevel.LOW }, mid = bands.firstOrNull { it.level == CloudLevel.MID }, high = bands.firstOrNull { it.level == CloudLevel.HIGH })

    private fun step(clouds: CloudMeta?, delta: Float, time: Double = 0.0) =
        occlusion.update(clouds, sun, 0f, 0f, radius, 100f, time, delta)

    private val overcast = clouds(CloudBand(CloudLevel.LOW, CloudType.CUMULUS, coverage = 1f))
    private val clear = clouds(CloudBand(CloudLevel.LOW, CloudType.CUMULUS, coverage = 0f))

    @Test
    fun aCloudOnTheSunRayDimsIt() {
        val value = step(overcast, 0f)
        assertTrue("dimmed to $value", value < 0.5f)
    }

    @Test
    fun aClearRayGivesFullSun() {
        assertEquals(1f, step(clear, 0f), 0f)
    }

    @Test
    fun noCloudsGiveFullSun() {
        assertEquals(1f, step(null, 0.016f), 0f)
        assertEquals(1f, step(CloudMeta(), 0.016f), 0f)
    }

    @Test
    fun stormFloorIsTenPercent() {
        val asIs = clouds(
            CloudBand(CloudLevel.LOW, CloudType.STRATOCUMULUS, base = 500f, top = 2200f, coverage = 0.92f, density = 1.6f, windX = 16f, windZ = 7f),
            CloudBand(CloudLevel.MID, CloudType.ALTOSTRATUS, base = 3000f, top = 6000f, coverage = 0.85f, density = 0.9f, windX = 24f, windZ = 9f),
        )
        val covered = asIs.copy(low = asIs.low?.copy(coverage = 1f), mid = asIs.mid?.copy(coverage = 1f), high = asIs.high?.copy(coverage = 1f))
        assertEquals(SUN_OCCLUSION_FLOOR, step(covered, 0f), 1e-6f)
        for (t in 0 until 200) {
            val value = occlusion.instant(asIs, sun, t * 97f, t * -61f, radius, 100f, t * 3.0)
            assertTrue("$value at $t", value >= SUN_OCCLUSION_FLOOR)
        }
    }

    @Test
    fun changesAreSmoothedOverAboutHalfASecond() {
        step(clear, 0f)
        var value = 1f
        repeat(30) { value = step(overcast, 1f / 60f) } // half a second at 60 frames per second
        val goal = occlusion.instant(overcast, sun, 0f, 0f, radius, 100f, 0.0)
        val reached = (1f - value) / (1f - goal)
        assertTrue("after 0.5 s ${reached * 100}% of the way", reached in 0.55f..0.7f)
        repeat(150) { value = step(overcast, 1f / 60f) }
        assertEquals(goal, value, 0.01f * (1f - goal))
    }

    @Test
    fun cloudsDisappearingResetAtOnce() {
        step(overcast, 0f)
        assertEquals(1f, step(null, 1f / 60f), 0f)
        assertTrue("starts from its target again", step(overcast, 1f / 60f) < 0.5f)
    }

    @Test
    fun theBandIsHitAboveTheViewer() {
        val up = bandDistance(Vector3(0f, 1f, 0f), 1500f, radius, 100f)!!
        assertEquals(1400f, up, 0.5f)
        val low = bandDistance(Vector3(1f, 0.05f, 0f).nor(), 1500f, radius, 100f)!!
        assertTrue("a low sun crosses the band far away: $low", low > 20_000f)
    }
}
