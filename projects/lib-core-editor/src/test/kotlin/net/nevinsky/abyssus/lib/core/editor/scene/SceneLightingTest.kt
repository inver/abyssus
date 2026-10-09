/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.editor.scene.MAX_DIRECTIONAL
import net.nevinsky.abyssus.lib.core.editor.scene.MAX_POINT
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.lib.core.editor.scene.lightSetOf
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement

import com.badlogic.gdx.graphics.Color
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
    private val origin = Vec3(0f, 0f, 0f)

    private fun light(
        kind: LightKind, intensity: Float = 1f, position: Vec3 = origin, color: Rgba = Rgba(1f, 0.5f, 0f, 1f),
        direction: Vec3 = Vec3(0f, -1f, 0f), id: String = "1",
    ) = LightPlacement(id, kind, color, intensity, position, direction)

    @Test
    fun onlyTheSunIsScaledByClouds() {
        val lights = listOf(
            light(LightKind.DIRECTIONAL, intensity = 2f, id = "7"),
            light(LightKind.DIRECTIONAL, intensity = 1f, id = "3", position = Vec3(5f, 0f, 0f)),
            light(LightKind.POINT, id = "4"),
            LightPlacement("8", LightKind.SPOT, Rgba(1f, 1f, 1f, 1f), 3f, origin, Vec3(0f, -1f, 0f), coneAngle = 40f),
        )
        val set = lightSetOf(lights, origin)
        val dimmed = set.withSunScale("7", 0.25f)
        assertEquals(Rgba(0.5f, 0.25f, 0f, 1f), dimmed.directional.first { it.entityId == "7" }.color)
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
    fun colorIsScaledByIntensity() {
        val set = lightSetOf(listOf(light(LightKind.DIRECTIONAL, intensity = 0.5f)), origin)
        assertEquals(Rgba(0.5f, 0.25f, 0f, 1f), set.directional.single().color)
        assertEquals(Vec3(0f, -1f, 0f), set.directional.single().direction)
    }

    @Test
    fun directionalLightsAreCappedKeepingTheNearest() {
        val lights = (1..5).map { light(LightKind.DIRECTIONAL, intensity = it.toFloat(), position = Vec3(it.toFloat(), 0f, 0f), id = "$it") }
        val set = lightSetOf(lights, origin)
        assertEquals(MAX_DIRECTIONAL, set.directional.size)
        assertEquals(listOf(1f, 2f), set.directional.map { it.color.r })
    }

    @Test
    fun pointLightsAreCappedKeepingTheNearest() {
        val lights = (1..8).map { light(LightKind.POINT, position = Vec3(it.toFloat(), 0f, 0f), id = "$it") }
        val set = lightSetOf(lights, origin)
        assertEquals(MAX_POINT, set.point.size)
        assertEquals((1..5).map { it.toFloat() }, set.point.map { it.position.x })
    }

    @Test
    fun pointRangeReachesTheEnvironmentAndBadRangeIsSkipped() {
        val good = LightPlacement("1", LightKind.POINT, Rgba(1f, 1f, 1f, 1f), 1f, origin, origin, range = 12f)
        val bad = good.copy(entityId = "2", range = 0f)
        val set = lightSetOf(listOf(good, bad), origin)
        assertEquals(listOf(12f), set.point.map { it.range })
        val env = Environment()
        set.applyTo(env)
        assertEquals(12f, env.get(PointLightsAttribute::class.java, PointLightsAttribute.Type)!!.lights.first().intensity, 0f)
    }

    @Test
    fun spotLightsKeepTheirIdentityAndBeamParameters() {
        val set = lightSetOf(listOf(light(LightKind.SPOT, position = Vec3(1f, 2f, 3f))), origin)
        assertTrue(set.directional.isEmpty())
        assertTrue(set.point.isEmpty())
        assertEquals(Vec3(1f, 2f, 3f), set.spot.single().position)
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
            light(LightKind.POINT, position = Vec3(Float.NaN, 0f, 0f)),
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
