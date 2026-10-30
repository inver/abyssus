/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RaySkyBakeTest {
    private val sun = Vec3(0f, 1f, 0f)
    private val project = File("project")
    private val clouds = Any()

    @Test
    fun stationaryCloudsAndBareSkiesAreBakedOncePerSunAndAsset() {
        val first = raySkyBakeKey("sky", project, sun, clouds, drifting = false, timeSeconds = 0.0)
        assertEquals(first, raySkyBakeKey("sky", project, sun, clouds, drifting = false, timeSeconds = 5000.0))
        val bare = raySkyBakeKey("sky", project, sun, null, drifting = true, timeSeconds = 0.0)
        assertEquals(bare, raySkyBakeKey("sky", project, sun, null, drifting = true, timeSeconds = 5000.0))
    }

    @Test
    fun driftingCloudsAreBakedAgainEveryRefreshPeriodOfCloudTime() {
        fun key(time: Double) = raySkyBakeKey("sky", project, sun, clouds, drifting = true, timeSeconds = time)
        assertEquals(key(0.0), key(1.999))
        assertNotEquals(key(0.0), key(5.0))
        assertEquals(key(5.0), key(9.9))
        assertNotEquals(key(5.0), key(10.0))
    }

    @Test
    fun aReloadedCloudAssetTheSunTheSkyAndTheProjectEachForceANewBake() {
        val base = raySkyBakeKey("sky", project, sun, clouds, false, 0.0)
        assertNotEquals(base, raySkyBakeKey("sky", project, sun, Any(), false, 0.0))
        assertNotEquals(base, raySkyBakeKey("sky", project, sun, null, false, 0.0))
        assertNotEquals(base, raySkyBakeKey("sky", project, Vec3(1f, 0f, 0f), clouds, false, 0.0))
        assertNotEquals(base, raySkyBakeKey("other", project, sun, clouds, false, 0.0))
        assertNotEquals(base, raySkyBakeKey("sky", File("elsewhere"), sun, clouds, false, 0.0))
    }

    @Test
    fun axisColorsFadeBetweenTwoLightsAndAreTheSameObjectWithoutATarget() {
        val from = FloatArray(18) { 0.2f }
        val to = FloatArray(18) { 1.0f }
        assertSame(from, blendAxisColors(from, null, 0.7f))
        assertArrayEquals(from, blendAxisColors(from, to, 0f), 0f)
        assertArrayEquals(FloatArray(18) { 0.6f }, blendAxisColors(from, to, 0.5f), 1e-6f)
        assertArrayEquals(to, blendAxisColors(from, to, 3f), 0f)
        assertArrayEquals(from, blendAxisColors(from, to, -1f), 0f)
        assertEquals(0.2f, from[0], 0f)
    }
}
