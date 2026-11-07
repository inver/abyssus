/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import net.nevinsky.abyssus.app.game.controlline.loadField
import net.nevinsky.abyssus.app.game.controlline.loadScene
import net.nevinsky.abyssus.lib.core.light.LightKind
import net.nevinsky.abyssus.lib.core.light.forwardOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scene lights the game reads (OpenSpec `control-line-pbr-rendering`, "Scene lights light the models"). */
class FieldSceneLightsTest {
    @Test
    fun theHangarHasItsLampsAndSpotsAndNoSun() {
        val hangar = FieldScene(loadScene("Hangar.scene"))
        assertFalse("the hangar has no directional light, so no sun", hangar.hasSun)
        assertEquals(64, hangar.localLights.count { it.kind == LightKind.POINT })
        assertEquals(4, hangar.localLights.count { it.kind == LightKind.SPOT })
        // every lamp light is one the editor would light with: a positive range and intensity
        assertTrue(hangar.localLights.all { it.range > 0f && it.intensity > 0f })
    }

    @Test
    fun theHangarSpotsFaceWhereTheEditorShowsThem() {
        val hangar = FieldScene(loadScene("Hangar.scene"))
        val spots = hangar.localLights.filter { it.kind == LightKind.SPOT }.associateBy { it.entityId }
        // the editor's rule: the rotated -Z (none of these looks at an entity)
        for (spot in spots.values) {
            val editor = forwardOf(spot.rotation)
            assertEquals("spot ${spot.entityId} x", editor.x, spot.direction.x, 1e-5f)
            assertEquals("spot ${spot.entityId} y", editor.y, spot.direction.y, 1e-5f)
            assertEquals("spot ${spot.entityId} z", editor.z, spot.direction.z, 1e-5f)
        }
        // the three over the table shine straight down
        for (id in listOf(2L, 6L, 7L)) assertEquals("spot $id down", -1f, spots.getValue(id).direction.y, 1e-4f)
    }

    @Test
    fun theFieldKeepsItsSun() {
        val field = FieldScene(loadField())
        assertTrue(field.hasSun)
        assertTrue(field.localLights.isEmpty())
    }
}
