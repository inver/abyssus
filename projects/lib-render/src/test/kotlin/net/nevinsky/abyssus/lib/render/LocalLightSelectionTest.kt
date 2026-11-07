/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.render.light.LightProfile
import net.nevinsky.abyssus.lib.render.light.PointInput
import net.nevinsky.abyssus.lib.render.light.SpotInput
import net.nevinsky.abyssus.lib.render.light.localLightWeight
import net.nevinsky.abyssus.lib.render.light.selectLocalLights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLightSelectionTest {
    /** A unit box from (0, 0, 0) to (1, 1, 1). */
    private val box = BoundingBox(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))

    private fun point(id: String, x: Float, y: Float, z: Float, range: Float, intensity: Float = 1f) =
        PointInput(id, Vector3(x, y, z), intensity, intensity, intensity, range)

    private fun spot(id: String, y: Float, range: Float) =
        SpotInput(id, Vector3(0.5f, y, 0.5f), Vector3(0f, -1f, 0f), 1f, 1f, 1f, range, 0.7f, 0.8f)

    @Test
    fun lightBeyondItsRangeIsLeftOut() {
        // 5 m above the box top with a range of 2 m: it cannot reach the model (spec "Beyond range")
        val chosen = selectLocalLights(listOf(point("far", 0.5f, 6f, 0.5f, 2f)), emptyList(), box)
        assertTrue(chosen.point.isEmpty())
    }

    @Test
    fun lightInsideOrNearTheBoxIsKept() {
        val inside = point("inside", 0.5f, 0.5f, 0.5f, 1f)
        val above = point("above", 0.5f, 2f, 0.5f, 3f)
        val chosen = selectLocalLights(listOf(above, inside), emptyList(), box)
        // inside the box the distance is 0: the strongest
        assertEquals(listOf("inside", "above"), chosen.point.map { it.entityId })
    }

    @Test
    fun strongestAreKeptWithinTheBudget() {
        val budget = LightProfile.GAME_SCENE_LIGHTS.maxPoint
        val lights = (1..budget + 4).map { point("p%03d".format(it), 0.5f, 1f + it, 0.5f, 1000f) }
        val chosen = selectLocalLights(lights.reversed(), emptyList(), box, LightProfile.GAME_SCENE_LIGHTS)
        assertEquals(budget, chosen.point.size)
        // the nearest are the strongest, and the order is strongest first
        assertEquals((1..budget).map { "p%03d".format(it) }, chosen.point.map { it.entityId })
    }

    @Test
    fun brighterFartherLightCanWin() {
        val dim = point("dim", 0.5f, 2f, 0.5f, 50f, intensity = 1f)
        val bright = point("bright", 0.5f, 4f, 0.5f, 50f, intensity = 100f)
        val chosen = selectLocalLights(listOf(dim, bright), emptyList(), box)
        assertEquals("bright", chosen.point.first().entityId)
    }

    @Test
    fun equalWeightsKeepAStableOrder() {
        val b = point("b", 0.5f, 3f, 0.5f, 10f)
        val a = point("a", 0.5f, 3f, 0.5f, 10f)
        assertEquals(listOf("a", "b"), selectLocalLights(listOf(b, a), emptyList(), box).point.map { it.entityId })
    }

    @Test
    fun spotsHaveTheirOwnBudget() {
        val spots = (1..LightProfile.GAME_SCENE_LIGHTS.maxSpot + 4).map { spot("s%02d".format(it), 1f + it, 1000f) }
        val chosen = selectLocalLights(emptyList(), spots, box)
        assertEquals(LightProfile.GAME_SCENE_LIGHTS.maxSpot, chosen.spot.size)
        assertTrue(chosen.directional.isEmpty())
    }

    @Test
    fun noBudgetSelectsNothing() {
        val chosen = selectLocalLights(listOf(point("p", 0.5f, 0.5f, 0.5f, 5f)), emptyList(), box, LightProfile.GAME_SINGLE_SUN)
        assertTrue(chosen.point.isEmpty())
    }

    @Test
    fun weightFadesToZeroAtTheRange() {
        assertEquals(1f, localLightWeight(1f, 1f, 1f, 4f, 0f), 1e-5f)
        // full within three quarters of the range, then fading
        assertEquals(1f / (1f + 9f), localLightWeight(1f, 1f, 1f, 4f, 3f), 1e-5f)
        assertTrue(localLightWeight(1f, 1f, 1f, 4f, 3.5f) in 0f..(1f / (1f + 12.25f)))
        assertEquals(0f, localLightWeight(1f, 1f, 1f, 4f, 4f))
        assertEquals(0f, localLightWeight(1f, 1f, 1f, 0f, 0f))
    }
}
