/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.render.frame.FrameInputs
import net.nevinsky.abyssus.lib.render.frame.RenderFog
import net.nevinsky.abyssus.lib.render.light.DirectionalInput
import net.nevinsky.abyssus.lib.render.light.packLights
import net.nevinsky.abyssus.lib.render.light.LightProfile
import net.nevinsky.abyssus.lib.render.light.PointInput
import net.nevinsky.abyssus.lib.render.light.RenderLights
import net.nevinsky.abyssus.lib.render.light.SpotInput
import net.nevinsky.abyssus.lib.render.shadow.sunShadowBias
import net.nevinsky.abyssus.lib.render.shadow.sunShadowCamera
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPlansTest {
    private fun dir(id: String) = DirectionalInput(id, Vector3(0f, -1f, 0f), 1f, 0.5f, 0.25f)
    private fun point(id: String) = PointInput(id, Vector3(1f, 2f, 3f), 1f, 1f, 1f, 10f)
    private fun spot(id: String) = SpotInput(id, Vector3(), Vector3(0f, 0f, -1f), 1f, 1f, 1f, 8f, 0.8f, 0.9f)

    @Test fun theEditorProfileKeepsSeveralLightsOfEveryKindUpToItsBudget() {
        val lights = RenderLights(listOf(dir("a"), dir("b"), dir("c")), List(7) { point("p$it") }, List(6) { spot("s$it") })
        val chosen = LightProfile.EDITOR_MULTI_LIGHT.select(lights)
        assertEquals(listOf("a", "b"), chosen.directional.map { it.entityId })
        assertEquals(5, chosen.point.size)
        assertEquals(5, chosen.spot.size)
    }

    @Test fun theGameProfileKeepsOnlyTheFirstSun() {
        val lights = RenderLights(listOf(dir("sun"), dir("other")), listOf(point("p")), listOf(spot("s")))
        val chosen = LightProfile.GAME_SINGLE_SUN.select(lights)
        assertEquals(listOf("sun"), chosen.directional.map { it.entityId })
        assertTrue(chosen.point.isEmpty() && chosen.spot.isEmpty())
    }

    @Test fun aSetWithinTheBudgetIsReturnedAsIs() {
        val lights = RenderLights(listOf(dir("a")))
        assertSame(lights, LightProfile.EDITOR_MULTI_LIGHT.select(lights))
        assertSame(lights, FrameInputs(Matrix4(), Vector3(), floatArrayOf(0f, 0f, 0f), null, lights, LightProfile.GAME_SINGLE_SUN).lights)
    }

    @Test fun packedArraysHoldEachLightInItsSlotAndZeroElsewhere() {
        val arrays = packLights(RenderLights(listOf(dir("a")), listOf(point("p")), listOf(spot("s"))), 5)
        assertEquals(1, arrays.directionalCount)
        assertEquals(15, arrays.directionalDirection.size)
        assertArrayEquals(floatArrayOf(0f, -1f, 0f, 0f, 0f), arrays.directionalDirection.copyOfRange(0, 5), 0f)
        assertArrayEquals(floatArrayOf(1f, 0.5f, 0.25f), arrays.directionalColor.copyOfRange(0, 3), 0f)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), arrays.pointPosition.copyOfRange(0, 3), 0f)
        assertEquals(10f, arrays.pointRange[0], 0f)
        assertEquals(0f, arrays.pointRange[1], 0f)
        assertEquals(0.8f, arrays.spotOuter[0], 0f)
        assertEquals(0.9f, arrays.spotInner[0], 0f)
        assertEquals(1, arrays.spotCount)
    }

    @Test fun packingDropsLightsPastTheSlots() {
        val arrays = packLights(RenderLights(point = List(8) { point("p$it") }), 5)
        assertEquals(5, arrays.pointCount)
        assertEquals(5, arrays.pointRange.size)
    }

    @Test fun theQuadraticFogCoefficientReachesTheExponentialShareAtTheCharacteristicDistance() {
        val fog = RenderFog(0.5f, 0.5f, 0.5f, density = 0.01f, gradient = 2f)
        val distance = 1f / fog.density
        assertEquals(1.0 - Math.exp(-1.0), (distance * distance * fog.quadraticCoefficient).toDouble(), 1e-6)
    }

    @Test fun theSunShadowCameraSnapsItsCentreToWholeTexelsAndIsStableWithinOne() {
        val sun = Vector3(0f, 1f, 0f)
        val a = sunShadowCamera(sun, Vector3(0f, 3f, 0f), 160f, 2048)
        val b = sunShadowCamera(sun, Vector3(0.001f, 3f, 0.001f), 160f, 2048)
        assertArrayEquals(a.combined.`val`, b.combined.`val`, 0f)
        assertEquals(160f, a.viewportWidth, 0f)
        assertEquals(400f, a.far, 0f)
        assertEquals(0.25f * 160f / 399f / 2048, sunShadowBias(a, 2048), 1e-9f)
    }
}
