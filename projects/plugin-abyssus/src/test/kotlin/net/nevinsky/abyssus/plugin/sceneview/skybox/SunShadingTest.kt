/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.lib.core.editor.scene.lightSetOf
import org.junit.Assert.*
import org.junit.Test

/** Which of the cloud shadow map and the uniform sun dimming applies, so the sun is never dimmed twice. */
class SunShadingTest {
    private val origin = Vector3(0f, 0f, 0f)

    private fun directional(id: String, intensity: Float, at: Vector3 = origin) =
        LightPlacement(id, LightKind.DIRECTIONAL, Color(1f, 1f, 1f, 1f), intensity, at, Vector3(0f, -1f, 0f))

    private val spot = LightPlacement(
        "8", LightKind.SPOT, Color(1f, 1f, 1f, 1f), 3f, origin, Vector3(0f, -1f, 0f), coneAngle = 40f,
    )

    private val set: LightSet = lightSetOf(listOf(directional("7", 2f), directional("3", 1f, Vector3(5f, 0f, 0f)), spot), origin)

    @Test
    fun anActiveMapKeepsTheSunUndimmedAndCarriesTheUniformScaleForOutsideTheMap() {
        val shading = planSunShading(set, "7", cloudsDrawn = true, mapReady = true, scale = 0.4f)
        assertSame("no double dimming: the sun keeps its color", set, shading.lights)
        assertEquals(set.directionalIndexOf("7"), shading.mapLightIndex)
        assertEquals(0.4f, shading.outside, 0f)
    }

    @Test
    fun withoutAMapTheSunIsDimmedUniformlyAsBefore() {
        for ((drawn, ready) in listOf((false to true), (true to false), (false to false))) {
            val shading = planSunShading(set, "7", cloudsDrawn = drawn, mapReady = ready, scale = 0.5f)
            assertNull("clouds drawn=$drawn map=$ready", shading.mapLightIndex)
            assertEquals(1f, shading.outside, 0f)
            assertEquals(set.directional.first { it.entityId == "7" }.color.r * 0.5f, shading.lights.directional.first { it.entityId == "7" }.color.r, 1e-6f)
        }
    }

    @Test
    fun otherLightsAreNeverChanged() {
        val mapped = planSunShading(set, "7", cloudsDrawn = true, mapReady = true, scale = 0.3f).lights
        val uniform = planSunShading(set, "7", cloudsDrawn = false, mapReady = false, scale = 0.3f).lights
        for (lights in listOf(mapped, uniform)) {
            assertEquals(set.directional.first { it.entityId == "3" }, lights.directional.first { it.entityId == "3" })
            assertEquals(set.spot, lights.spot)
            assertEquals(set.point, lights.point)
        }
    }

    @Test
    fun aSunOutsideTheRenderedBudgetOrMissingShadesNoOtherLight() {
        val crowded = lightSetOf(
            listOf(directional("a", 1f, Vector3(1f, 0f, 0f)), directional("b", 1f, Vector3(2f, 0f, 0f)), directional("sun", 9f, Vector3(9f, 0f, 0f))),
            origin,
        )
        for (sunId in listOf("sun", null, "missing")) {
            val shading = planSunShading(crowded, sunId, cloudsDrawn = true, mapReady = true, scale = 0.2f)
            assertNull("sun=$sunId", shading.mapLightIndex)
            assertSame("nothing is scaled", crowded, shading.lights)
        }
        val none = planSunShading(NO_LIGHTS, null, cloudsDrawn = true, mapReady = true, scale = 0.2f)
        assertNull(none.mapLightIndex)
        assertSame(NO_LIGHTS, none.lights)
    }

    @Test
    fun aClearSkyChangesNothing() {
        val shading = planSunShading(set, "7", cloudsDrawn = false, mapReady = false, scale = 1f)
        assertSame(set, shading.lights)
        assertNull(shading.mapLightIndex)
    }
}
