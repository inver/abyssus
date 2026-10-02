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

package net.nevinsky.abyssus.sceneview.skybox

import net.nevinsky.abyssus.sceneview.LightKind
import net.nevinsky.abyssus.sceneview.LightPlacement
import net.nevinsky.abyssus.sceneview.Vec3
import kotlin.math.sqrt

/** Where the sun is: the unit vector from the viewer toward it. */
object SunDirection {
    /** 45 degrees above the horizon, used when the scene has no usable directional light. */
    val DEFAULT = Vec3(0f, 0.70710678f, 0.70710678f)

    /** Opposite of the brightest directional light's direction; [DEFAULT] when there is none. */
    fun of(lights: List<LightPlacement>): Vec3 {
        val light = lights.filter { it.kind == LightKind.DIRECTIONAL && usable(it) }.maxByOrNull { it.intensity } ?: return DEFAULT
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
