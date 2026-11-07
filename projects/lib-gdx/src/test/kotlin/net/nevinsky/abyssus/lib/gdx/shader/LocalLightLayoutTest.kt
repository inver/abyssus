/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.utils.Array
import org.junit.Assert.assertEquals
import org.junit.Test

/** The local light texture's layout is named by Kotlin and by both lit fragment shaders: they must agree. */
class LocalLightLayoutTest {
    private fun define(source: String, name: String): Float =
        Regex("""#define $name ([0-9.]+)""").find(source)!!.groupValues[1].toFloat()

    @Test
    fun bothShadersUseTheLayoutOfTheLightTexture() {
        for (resource in listOf(ShaderSources.DEFAULT_FRAGMENT, ShaderSources.PBR_FRAGMENT)) {
            val source = ShaderSources.read(resource)
            assertEquals(resource, LOCAL_LIGHT_WIDTH.toFloat(), define(source, "LOCAL_LIGHT_WIDTH"), 0f)
            assertEquals(resource, LOCAL_LIGHT_HEIGHT.toFloat(), define(source, "LOCAL_LIGHT_HEIGHT"), 0f)
            assertEquals(resource, LOCAL_LIGHT_SPOT_ROW.toFloat(), define(source, "LOCAL_LIGHT_SPOT_ROW"), 0f)
        }
    }

    @Test
    fun packingKeepsAtMostTheCapacityOfEachKind() {
        val bindings = LocalLightBindings()
        val points = Array<PointLight>()
        val spots = Array<SpotLight>()
        repeat(LOCAL_LIGHT_MAX + 10) {
            points.add(PointLight())
            spots.add(SpotLight())
        }
        bindings.pack(points, spots)
        assertEquals(LOCAL_LIGHT_MAX, bindings.points)
        assertEquals(LOCAL_LIGHT_MAX, bindings.spots)
        bindings.pack(null, null)
        assertEquals(0, bindings.points)
    }
}
