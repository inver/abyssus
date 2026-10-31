/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.editor.scene.MAX_DIRECTIONAL
import net.nevinsky.abyssus.lib.core.editor.scene.MAX_POINT
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.lib.core.editor.scene.lightSetOf
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement

import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLightingTest {
    private val origin = Vector3(0f, 0f, 0f)

    private fun light(
        kind: LightKind, intensity: Float = 1f, position: Vector3 = origin, color: Color = Color(1f, 0.5f, 0f, 1f),
        direction: Vector3 = Vector3(0f, -1f, 0f), id: String = "1",
    ) = LightPlacement(id, kind, color, intensity, position, direction)

    @Test
    fun onlyTheSunIsScaledByClouds() {
        val lights = listOf(
            light(LightKind.DIRECTIONAL, intensity = 2f, id = "7"),
            light(LightKind.DIRECTIONAL, intensity = 1f, id = "3", position = Vector3(5f, 0f, 0f)),
            light(LightKind.POINT, id = "4"),
            LightPlacement("8", LightKind.SPOT, Color(1f, 1f, 1f, 1f), 3f, origin, Vector3(0f, -1f, 0f), coneAngle = 40f),
        )
        val set = lightSetOf(lights, origin)
        val dimmed = set.withSunScale("7", 0.25f)
        assertEquals(Color(0.5f, 0.25f, 0f, 1f), dimmed.directional.first { it.entityId == "7" }.color)
        assertEquals(set.directional.first { it.entityId == "3" }, dimmed.directional.first { it.entityId == "3" })
        assertEquals(set.point, dimmed.point)
        assertEquals(set.spot, dimmed.spot)
        assertTrue("a full sun keeps the same set", set.withSunScale("7", 1f) === set)
        assertTrue("no sun keeps the same set", set.withSunScale(null, 0.5f) === set)

        val environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.2f, 0.3f, 0.4f, 1f))
        dimmed.applyTo(environment)
        val ambient = environment.get(ColorAttribute::class.java, ColorAttribute.AmbientLight)!!.color
        assertEquals(Color(0.2f, 0.3f, 0.4f, 1f), ambient)
        assertEquals(1, environment.get(SpotLightsAttribute::class.java, SpotLightsAttribute.Type)!!.lights.size)
    }

    @Test
    fun theSunsDirectionalIndexFollowsTheBudgetedSet() {
        val lights = listOf(
            light(LightKind.DIRECTIONAL, intensity = 1f, position = Vector3(5f, 0f, 0f), id = "far"),
            light(LightKind.DIRECTIONAL, intensity = 3f, position = Vector3(1f, 0f, 0f), id = "near"),
            light(LightKind.DIRECTIONAL, intensity = 9f, position = Vector3(9f, 0f, 0f), id = "bright but beyond the budget"),
            light(LightKind.POINT, intensity = 2f, id = "point"),
        )
        val set = lightSetOf(lights, origin)
        assertEquals(listOf("near", "far"), set.directional.map { it.entityId })
        assertEquals(0, set.directionalIndexOf("near"))
        assertEquals(1, set.directionalIndexOf("far"))
        assertNull("beyond the directional budget", set.directionalIndexOf("bright but beyond the budget"))
        assertNull("a point light is not directional", set.directionalIndexOf("point"))
        assertNull(set.directionalIndexOf("missing"))
        assertNull(set.directionalIndexOf(null))
        assertNull(NO_LIGHTS.directionalIndexOf("near"))
    }

    @Test
    fun colorIsScaledByIntensity() {
        val set = lightSetOf(listOf(light(LightKind.DIRECTIONAL, intensity = 0.5f)), origin)
        assertEquals(Color(0.5f, 0.25f, 0f, 1f), set.directional.single().color)
        assertEquals(Vector3(0f, -1f, 0f), set.directional.single().direction)
    }

    @Test
    fun directionalLightsAreCappedKeepingTheNearest() {
        val lights = (1..5).map { light(LightKind.DIRECTIONAL, intensity = it.toFloat(), position = Vector3(it.toFloat(), 0f, 0f), id = "$it") }
        val set = lightSetOf(lights, origin)
        assertEquals(MAX_DIRECTIONAL, set.directional.size)
        assertEquals(listOf(1f, 2f), set.directional.map { it.color.r })
    }

    @Test
    fun pointLightsAreCappedKeepingTheNearest() {
        val lights = (1..8).map { light(LightKind.POINT, position = Vector3(it.toFloat(), 0f, 0f), id = "$it") }
        val set = lightSetOf(lights, origin)
        assertEquals(MAX_POINT, set.point.size)
        assertEquals((1..5).map { it.toFloat() }, set.point.map { it.position.x })
    }

    @Test
    fun pointRangeReachesTheEnvironmentAndBadRangeIsSkipped() {
        val good = LightPlacement("1", LightKind.POINT, Color(1f, 1f, 1f, 1f), 1f, origin, origin, range = 12f)
        val bad = good.copy(entityId = "2", range = 0f)
        val set = lightSetOf(listOf(good, bad), origin)
        assertEquals(listOf(12f), set.point.map { it.range })
        val env = Environment()
        set.applyTo(env)
        assertEquals(12f, env.get(PointLightsAttribute::class.java, PointLightsAttribute.Type)!!.lights.first().intensity, 0f)
    }

    @Test
    fun spotLightsKeepTheirIdentityAndBeamParameters() {
        val set = lightSetOf(listOf(light(LightKind.SPOT, position = Vector3(1f, 2f, 3f))), origin)
        assertTrue(set.directional.isEmpty())
        assertTrue(set.point.isEmpty())
        assertEquals(Vector3(1f, 2f, 3f), set.spot.single().position)
        assertEquals("1", set.spot.single().entityId)
        assertEquals(45f, set.spot.single().cone.angle, 0f)
        assertEquals(0.2f, set.spot.single().cone.softness, 0f)
    }

    @Test
    fun localBudgetIsSharedWithStableIdentityTiesAndInvalidBeamsSkipped() {
        val lights = (1..8).map { light(if (it % 2 == 0) LightKind.SPOT else LightKind.POINT, id = "$it") }
        val a = lightSetOf(lights, origin)
        val b = lightSetOf(lights.reversed(), origin)
        assertEquals(5, a.point.size + a.spot.size)
        assertEquals(listOf("1", "3", "5"), a.point.map { it.entityId })
        assertEquals(listOf("2", "4"), a.spot.map { it.entityId })
        assertEquals(a.point, b.point)
        assertEquals(a.spot.map { it.entityId }, b.spot.map { it.entityId })
        val invalid = listOf(light(LightKind.SPOT).copy(coneAngle = 180f), light(LightKind.SPOT).copy(edgeSoftness = Float.NaN))
        assertTrue(lightSetOf(invalid, origin).spot.isEmpty())
    }

    @Test
    fun unusableLightsAreSkipped() {
        val lights = listOf(
            light(LightKind.DIRECTIONAL, intensity = 0f),
            light(LightKind.DIRECTIONAL, intensity = Float.NaN),
            light(LightKind.POINT, position = Vector3(Float.NaN, 0f, 0f)),
            light(LightKind.DIRECTIONAL, intensity = 1f),
        )
        val set = lightSetOf(lights, origin)
        assertEquals(1, set.directional.size)
        assertTrue(set.point.isEmpty())
    }

    @Test
    fun applyReplacesLightsInTheEnvironment() {
        val env = Environment()
        lightSetOf(listOf(light(LightKind.DIRECTIONAL), light(LightKind.POINT)), origin).applyTo(env)
        assertEquals(1, env.get(DirectionalLightsAttribute::class.java, DirectionalLightsAttribute.Type)!!.lights.size)
        assertEquals(1, env.get(PointLightsAttribute::class.java, PointLightsAttribute.Type)!!.lights.size)
        NO_LIGHTS.applyTo(env)
        assertNull(env.get(DirectionalLightsAttribute.Type))
        assertNull(env.get(PointLightsAttribute.Type))
        assertNotNull(env)
    }
}
