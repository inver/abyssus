/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.light

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.LightDto
import net.nevinsky.abyssus.lib.core.format.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLightsTest {
    private fun light(
        id: Long,
        kind: LightKind,
        at: Vector3 = Vector3(),
        intensity: Float = 1f,
        range: Float = 10f,
        cone: Float = 45f,
        softness: Float = 0.2f,
        direction: Vector3 = Vector3(0f, -1f, 0f),
    ) = LightPlacement(id, kind, Color(1f, 0.5f, 0.25f, 1f), intensity, at, direction, range, Quaternion(), cone, softness)

    @Test
    fun kindFollowsTheEntityType() {
        assertEquals(LightKind.POINT, lightKindOf(EntityType.LIGHT_POINT, true))
        assertEquals(LightKind.SPOT, lightKindOf(EntityType.LIGHT_SPOT, true))
        assertEquals(LightKind.DIRECTIONAL, lightKindOf(EntityType.LIGHT_DIRECTIONAL, true))
        // an object that still has a light component is a directional light; without one it is no light
        assertEquals(LightKind.DIRECTIONAL, lightKindOf(EntityType.OBJECT, true))
        assertNull(lightKindOf(EntityType.OBJECT, false))
        assertNull(lightKindOf(null, false))
    }

    @Test
    fun placementReadsTheLightValues() {
        val dto = LightDto(intensity = -2f, range = 7f, coneAngle = 30f, edgeSoftness = 0.5f)
        val turnedDown = Quaternion().setFromAxis(1f, 0f, 0f, -90f)
        val l = lightPlacementOf(3L, LightKind.SPOT, dto, Vector3(1f, 4f, 2f), turnedDown, 9L)
        assertEquals(0f, l.intensity) // a negative intensity reads as 0
        assertEquals(7f, l.range)
        assertEquals(30f, l.coneAngle)
        assertEquals(0.5f, l.edgeSoftness)
        assertEquals(9L, l.lookAtId)
        assertEquals(-1f, l.direction.y, 1e-5f)
        // no light values: the documented defaults, a white light
        val defaults = lightPlacementOf(4L, LightKind.POINT, null, Vector3(), Quaternion())
        assertEquals(100f, defaults.range)
        assertEquals(Color.WHITE, defaults.color)
    }

    @Test
    fun unusableLightsAreSkipped() {
        assertTrue(usable(light(1, LightKind.POINT)))
        assertFalse(usable(light(1, LightKind.POINT, intensity = 0f)))
        assertFalse(usable(light(1, LightKind.POINT, range = 0f)))
        assertFalse(usable(light(1, LightKind.POINT, at = Vector3(Float.NaN, 0f, 0f))))
        assertFalse(usable(light(1, LightKind.SPOT, cone = 180f)))
        assertFalse(usable(light(1, LightKind.SPOT, softness = 1.5f)))
        assertFalse(usable(light(1, LightKind.SPOT, direction = Vector3())))
        // a point light ignores the cone
        assertTrue(usable(light(1, LightKind.POINT, cone = 180f)))
    }

    @Test
    fun colourIsScaledByIntensityWithoutClamping() {
        val c = scaledColor(light(1, LightKind.POINT, intensity = 4f))
        assertEquals(4f, c.r)
        assertEquals(2f, c.g)
        assertEquals(1f, c.b)
    }

    @Test
    fun selectionKeepsTheNearestWithinBudgets() {
        val lights =
            listOf(
                light(5, LightKind.POINT, Vector3(5f, 0f, 0f)),
                light(2, LightKind.SPOT, Vector3(1f, 0f, 0f)),
                light(3, LightKind.POINT, Vector3(1f, 0f, 0f)),
                light(4, LightKind.POINT, Vector3(9f, 0f, 0f), intensity = 0f),
                light(1, LightKind.DIRECTIONAL, Vector3(50f, 0f, 0f)),
                light(6, LightKind.DIRECTIONAL, Vector3(2f, 0f, 0f)),
            )
        val selected = selectLights(lights, Vector3(), maxDirectional = 1, maxLocal = 2)
        assertEquals(listOf(6L), selected.directional.map { it.entityId })
        // equal distance: the lower entity id first; the unusable light 4 never counts
        assertEquals(listOf(2L, 3L), selected.local.map { it.entityId })
    }

    @Test
    fun libGdxLightsCarryRangeAndCone() {
        val point = pointLightOf(light(1, LightKind.POINT, Vector3(1f, 2f, 3f), range = 8f))
        assertEquals(8f, point.intensity)
        // an intensity above 1 still brightens: the colour is not clamped
        assertEquals(2f, pointLightOf(light(3, LightKind.POINT, intensity = 2f)).color.r)
        assertEquals(Vector3(1f, 2f, 3f), point.position)
        val spot = spotLightOf(light(2, LightKind.SPOT, range = 6f, cone = 50f, softness = 0.3f))
        assertEquals(6f, spot.intensity)
        assertEquals(25f, spot.cutoffAngle)
        assertEquals(0.3f, spot.exponent)
    }
}
