/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.scene.DirectionalSource
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which of the rendered directional lights the cloud shadows shade. */
class SunLightIndexTest {
    private fun directional(id: String) = DirectionalSource(Vec3(0f, -1f, 0f), Rgba(1f, 1f, 1f, 1f), id)

    private val lights = LightSet(listOf(directional("10"), directional("7")), emptyList())

    @Test
    fun theSunIsFoundByEntityIdNotByPosition() {
        assertEquals(1, sunLightIndex(lights, "7"))
        assertEquals(0, sunLightIndex(lights, "10"))
    }

    @Test
    fun aSunOutsideTheRenderedLightsShadesNothing() {
        // beyond the directional budget, or without a usable direction, the sun is not in the set: never another light
        assertEquals(-1, sunLightIndex(lights, "3"))
        assertEquals(-1, sunLightIndex(NO_LIGHTS, "7"))
    }

    @Test
    fun noSunMeansNoIndex() {
        assertEquals(-1, sunLightIndex(lights, null))
    }
}
