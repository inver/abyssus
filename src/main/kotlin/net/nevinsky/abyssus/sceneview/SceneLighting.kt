/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import kotlin.math.sqrt

/** A directional light: [color] already multiplied by the light's intensity. */
data class DirectionalSource(val direction: Vec3, val color: Rgba)

/** A point light: [color] already multiplied by the light's intensity. */
data class PointSource(val position: Vec3, val color: Rgba, val range: Float = DEFAULT_LIGHT_RANGE)

/**
 * The light entities of a scene as the renderers use them, limited to what the shaders support
 * ([MAX_DIRECTIONAL] / [MAX_POINT], Mundus' shader defaults).
 *
 * Spot lights are not implemented by the copied Mundus shaders; they are drawn as point lights at their position.
 */
class LightSet(val directional: List<DirectionalSource>, val point: List<PointSource>) {
    /** Replaces the directional and point lights of [environment]; the ambient light is left alone. */
    fun applyTo(environment: Environment) {
        environment.remove(DirectionalLightsAttribute.Type)
        environment.remove(PointLightsAttribute.Type)
        for (d in directional) {
            environment.add(DirectionalLight().set(Color(d.color.r, d.color.g, d.color.b, 1f), d.direction.x, d.direction.y, d.direction.z))
        }
        for (p in point) {
            environment.add(PointLight().set(Color(p.color.r, p.color.g, p.color.b, 1f), p.position.x, p.position.y, p.position.z, p.range))
        }
    }

    companion object {
        const val MAX_DIRECTIONAL = 2
        const val MAX_POINT = 5

        val NONE = LightSet(emptyList(), emptyList())

        /** The brightest directional lights and the point lights nearest [target]; unusable lights are skipped. */
        fun of(lights: List<LightPlacement>, target: Vec3): LightSet {
            val directional = lights.filter { it.kind == LightKind.DIRECTIONAL && usable(it) }
                .sortedByDescending { it.intensity }
                .take(MAX_DIRECTIONAL)
                .map { DirectionalSource(it.direction, scaled(it)) }
            val point = lights.filter { it.kind != LightKind.DIRECTIONAL && usable(it) }
                .sortedBy { distance(it.position, target) }
                .take(MAX_POINT)
                .map { PointSource(it.position, scaled(it), it.range) }
            return LightSet(directional, point)
        }

        private fun usable(l: LightPlacement) = l.intensity > 0f && l.intensity.isFinite() && l.range > 0f && l.range.isFinite() &&
            listOf(l.color.r, l.color.g, l.color.b, l.position.x, l.position.y, l.position.z, l.direction.x, l.direction.y, l.direction.z)
                .all { it.isFinite() }

        private fun scaled(l: LightPlacement) = Rgba(l.color.r * l.intensity, l.color.g * l.intensity, l.color.b * l.intensity, 1f)

        private fun distance(a: Vec3, b: Vec3): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            val dz = a.z - b.z
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
    }
}
