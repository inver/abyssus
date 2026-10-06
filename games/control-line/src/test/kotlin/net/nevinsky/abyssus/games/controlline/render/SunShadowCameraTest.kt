/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.render

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SunShadowCameraTest {
    private val towardSun = Vector3(60f, 100f, 40f).nor()

    private fun project(point: Vector3): Vector3 = Vector3(point).prj(sunShadowCamera(towardSun, Vector3.Zero).combined)

    @Test
    fun pilotIsInTheMiddleOfTheMap() {
        val center = project(Vector3.Zero)
        assertEquals(0f, center.x, 1e-3f)
        assertEquals(0f, center.y, 1e-3f)
    }

    @Test
    fun fieldFacilitiesStreetAndRailwayAreInTheMap() {
        for (point in listOf(Vector3(55f, 0f, 0f), Vector3(-55f, 0f, 0f), Vector3(0f, 0f, -55f), Vector3(0f, -2f, 70f))) {
            val p = project(point)
            assertTrue("$point maps to $p", abs(p.x) < 1f && abs(p.y) < 1f && abs(p.z) < 1f)
        }
    }

    @Test
    fun casterTowardTheSunCoversTheGroundBelowIt() {
        val ground = Vector3(10f, 0f, 5f)
        val caster = Vector3(ground).mulAdd(towardSun, 12f)
        val g = project(ground)
        val c = project(caster)
        assertEquals(g.x, c.x, 1e-4f)
        assertEquals(g.y, c.y, 1e-4f)
        assertTrue("the caster is nearer the sun", c.z < g.z)
    }

    @Test
    fun centreSnapsToWholeTexels() {
        val texel = SHADOW_EXTENT / SHADOW_MAP_SIZE
        val a = Vector3(Vector3.Zero).prj(sunShadowCamera(towardSun, Vector3(0.01f, 0f, 0f)).combined)
        val b = Vector3(Vector3.Zero).prj(sunShadowCamera(towardSun, Vector3.Zero).combined)
        assertEquals(b.x, a.x, 1e-4f)
        assertTrue(texel > 0.01f)
    }

    @Test
    fun biasIsAFractionOfATexelInDepth() {
        val bias = sunShadowBias(sunShadowCamera(towardSun, Vector3.Zero))
        assertTrue("bias $bias", bias > 0f && bias < 1e-3f)
    }
}
