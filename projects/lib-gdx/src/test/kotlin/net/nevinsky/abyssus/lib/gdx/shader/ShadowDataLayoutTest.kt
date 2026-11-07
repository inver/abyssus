/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Kotlin side and `shadow_visibility.glsl` both name the data texture's layout: they must agree. */
class ShadowDataLayoutTest {
    private val source = javaClass.getResource("/shader/shared/shadow_visibility.glsl")!!.readText()

    private fun define(name: String): Float =
        Regex("""#define $name ([0-9.]+)""").find(source)!!.groupValues[1].toFloat()

    @Test
    fun theShaderUsesTheLayoutOfTheDataTexture() {
        assertEquals(SHADOW_DATA_WIDTH.toFloat(), define("SHADOW_DATA_WIDTH"), 0f)
        assertEquals(SHADOW_DATA_HEIGHT.toFloat(), define("SHADOW_DATA_HEIGHT"), 0f)
        assertEquals(SHADOW_DIRECTIONAL_TABLE_ROW.toFloat(), define("SHADOW_DIRECTIONAL_TABLE_ROW"), 0f)
        assertEquals(SHADOW_POINT_TABLE_ROW.toFloat(), define("SHADOW_POINT_TABLE_ROW"), 0f)
        assertEquals(SHADOW_SPOT_TABLE_ROW.toFloat(), define("SHADOW_SPOT_TABLE_ROW"), 0f)
    }

    @Test
    fun theViewsAndTablesFitTheTextureWithoutOverlap() {
        assertTrue(SHADOW_MAX_VIEWS >= SHADOW_MAX_DIRECTIONAL + SHADOW_MAX_LOCAL * SHADOW_POINT_FACES)
        assertTrue(SHADOW_DIRECTIONAL_TABLE_ROW >= SHADOW_VIEW_ROWS)
        assertTrue(SHADOW_POINT_TABLE_ROW > SHADOW_DIRECTIONAL_TABLE_ROW)
        assertEquals(SHADOW_POINT_TABLE_ROW + SHADOW_MAX_LOCAL / SHADOW_DATA_WIDTH, SHADOW_SPOT_TABLE_ROW)
        assertTrue(SHADOW_SPOT_TABLE_ROW + SHADOW_MAX_LOCAL / SHADOW_DATA_WIDTH <= SHADOW_DATA_HEIGHT)
    }
}
