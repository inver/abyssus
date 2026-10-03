/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLightingTest {
    private val origin = Vec3(0f, 0f, 0f)

    private fun light(
        kind: LightKind, intensity: Float = 1f, position: Vec3 = origin, color: Rgba = Rgba(1f, 0.5f, 0f, 1f),
        direction: Vec3 = Vec3(0f, -1f, 0f), id: String = "1",
    ) = LightPlacement(id, kind, color, intensity, position, direction)

    @Test
    fun colorIsScaledByIntensity() {
        val set = LightSet.of(listOf(light(LightKind.DIRECTIONAL, intensity = 0.5f)), origin)
        assertEquals(Rgba(0.5f, 0.25f, 0f, 1f), set.directional.single().color)
        assertEquals(Vec3(0f, -1f, 0f), set.directional.single().direction)
    }

    @Test
    fun directionalLightsAreCappedKeepingTheBrightest() {
        val lights = (1..5).map { light(LightKind.DIRECTIONAL, intensity = it.toFloat(), id = "$it") }
        val set = LightSet.of(lights, origin)
        assertEquals(LightSet.MAX_DIRECTIONAL, set.directional.size)
        assertEquals(listOf(5f, 4f), set.directional.map { it.color.r })
    }

    @Test
    fun pointLightsAreCappedKeepingTheNearest() {
        val lights = (1..8).map { light(LightKind.POINT, position = Vec3(it.toFloat(), 0f, 0f), id = "$it") }
        val set = LightSet.of(lights, origin)
        assertEquals(LightSet.MAX_POINT, set.point.size)
        assertEquals((1..5).map { it.toFloat() }, set.point.map { it.position.x })
    }

    @Test
    fun pointRangeReachesTheEnvironmentAndBadRangeIsSkipped() {
        val good = LightPlacement("1", LightKind.POINT, Rgba(1f, 1f, 1f, 1f), 1f, origin, origin, range = 12f)
        val bad = good.copy(entityId = "2", range = 0f)
        val set = LightSet.of(listOf(good, bad), origin)
        assertEquals(listOf(12f), set.point.map { it.range })
        val env = Environment()
        set.applyTo(env)
        assertEquals(12f, env.get(PointLightsAttribute::class.java, PointLightsAttribute.Type)!!.lights.first().intensity, 0f)
    }

    @Test
    fun spotLightsFallBackToPointLights() {
        val set = LightSet.of(listOf(light(LightKind.SPOT, position = Vec3(1f, 2f, 3f))), origin)
        assertTrue(set.directional.isEmpty())
        assertEquals(Vec3(1f, 2f, 3f), set.point.single().position)
    }

    @Test
    fun unusableLightsAreSkipped() {
        val lights = listOf(
            light(LightKind.DIRECTIONAL, intensity = 0f),
            light(LightKind.DIRECTIONAL, intensity = Float.NaN),
            light(LightKind.POINT, position = Vec3(Float.NaN, 0f, 0f)),
            light(LightKind.DIRECTIONAL, intensity = 1f),
        )
        val set = LightSet.of(lights, origin)
        assertEquals(1, set.directional.size)
        assertTrue(set.point.isEmpty())
    }

    @Test
    fun applyReplacesLightsInTheEnvironment() {
        val env = Environment()
        LightSet.of(listOf(light(LightKind.DIRECTIONAL), light(LightKind.POINT)), origin).applyTo(env)
        assertEquals(1, env.get(DirectionalLightsAttribute::class.java, DirectionalLightsAttribute.Type)!!.lights.size)
        assertEquals(1, env.get(PointLightsAttribute::class.java, PointLightsAttribute.Type)!!.lights.size)
        LightSet.NONE.applyTo(env)
        assertNull(env.get(DirectionalLightsAttribute.Type))
        assertNull(env.get(PointLightsAttribute.Type))
        assertNotNull(env)
    }
}
