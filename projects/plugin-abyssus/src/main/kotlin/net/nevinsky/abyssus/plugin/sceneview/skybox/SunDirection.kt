/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import net.nevinsky.abyssus.lib.gdx.editor.content.LightKind
import net.nevinsky.abyssus.lib.gdx.editor.content.LightPlacement
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3
import kotlin.math.sqrt

/** Where the sun is: the unit vector from the viewer toward it. */
object SunDirection {
    /** 45 degrees above the horizon, used when the scene has no usable directional light. */
    val DEFAULT = Vec3(0f, 0.70710678f, 0.70710678f)

    /**
     * The sun: the brightest usable directional light, or null when there is none. Both the procedural sky's sun
     * ([of]) and the light that clouds dim come from this one rule, so they cannot diverge.
     */
    fun sunLight(lights: List<LightPlacement>): LightPlacement? =
        lights.filter { it.kind == LightKind.DIRECTIONAL && usable(it) }.maxByOrNull { it.intensity }

    /** Opposite of the brightest directional light's direction; [DEFAULT] when there is none. */
    fun of(lights: List<LightPlacement>): Vec3 {
        val light = sunLight(lights) ?: return DEFAULT
        val d = light.direction
        val length = sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
        return Vec3(0f - d.x / length, 0f - d.y / length, 0f - d.z / length)
    }

    private fun usable(l: LightPlacement): Boolean {
        val d = l.direction
        return l.intensity > 0f && l.intensity.isFinite() && listOf(d.x, d.y, d.z).all { it.isFinite() } &&
            d.x * d.x + d.y * d.y + d.z * d.z > 1e-12f
    }
}
