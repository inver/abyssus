/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render

import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_UNIT
import net.nevinsky.abyssus.lib.core.util.*
import net.nevinsky.abyssus.lib.core.water.MAX_DIRECTIONAL
import net.nevinsky.abyssus.lib.render.light.LightProfile
import net.nevinsky.abyssus.lib.render.terrain.SHADOW_UNIT
import net.nevinsky.abyssus.lib.render.terrain.MAX
import org.junit.Assert.*
import org.junit.Test

class ShaderBudgetsTest {
    @Test
    fun terrainUploadCapacityCoversEveryBundledEditorLightArray() {
        val profile = LightProfile.EDITOR_MULTI_LIGHT
        assertEquals(maxOf(profile.maxDirectional, profile.maxPoint, profile.maxSpot), MAX)
        val source = javaClass.getResource("/shader/render/terrain_editor.frag")!!.readText()
        val sizes = Regex("""uniform\s+\w+\s+u_(?:dir|point|spot)\w*\[(\d+)]""")
            .findAll(source).map { it.groupValues[1].toInt() }.toList()
        assertTrue("No editor light arrays found", sizes.isNotEmpty())
        for (size in sizes) assertTrue("Upload capacity $MAX cannot hold shader array size $size", MAX >= size)
    }

    @Test
    fun lightProfilesAndTextureBindingsUseTheShaderContract() {
        val editor = LightProfile.EDITOR_MULTI_LIGHT
        assertEquals(EDITOR_MAX_DIRECTIONAL, editor.maxDirectional)
        assertEquals(EDITOR_MAX_POINT, editor.maxPoint)
        assertEquals(EDITOR_MAX_SPOT, editor.maxSpot)
        assertEquals(listOf(2, 5, 5), listOf(editor.maxDirectional, editor.maxPoint, editor.maxSpot))
        val game = LightProfile.GAME_SINGLE_SUN
        assertEquals(GAME_MAX_DIRECTIONAL, game.maxDirectional)
        assertEquals(GAME_MAX_POINT, game.maxPoint)
        assertEquals(GAME_MAX_SPOT, game.maxSpot)
        assertEquals(WATER_DIRECTIONAL_CAPACITY, MAX_DIRECTIONAL)
        assertEquals(5, MAX_DIRECTIONAL)
        assertEquals(TERRAIN_SPLAT_TEXTURE_UNIT, SPLAT_UNIT)
        assertEquals(TERRAIN_SHADOW_TEXTURE_UNIT, SHADOW_UNIT)
        assertTrue(SHADOW_UNIT > SPLAT_UNIT)
        assertEquals(SPLAT_UNIT + 1, SHADOW_UNIT)
    }
}
