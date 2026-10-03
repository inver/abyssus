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

import net.nevinsky.abyssus.sceneview.Vec3
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The mapping between world directions and equirectangular image coordinates (`u`, `v` in 0..1, `v` = 0 at the top
 * row). The image's horizontal centre faces `-Z`, its left and right edges `+Z`, `u` = 0.75 faces `+X`, and its top
 * row is straight up. `/shader/scene/equirect.glsl` holds the same formulas.
 */
object Equirect {
    /** (u, v) of the direction [d], which need not be normalized. */
    fun uv(d: Vec3): Pair<Float, Float> {
        val len = sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
        val u = 0.5f + (atan2(d.x, -d.z) / (2 * PI)).toFloat()
        val v = (acos((d.y / len).coerceIn(-1f, 1f)) / PI).toFloat()
        return u to v
    }

    /** The unit direction at ([u], [v]). */
    fun direction(u: Float, v: Float): Vec3 {
        val phi = (u - 0.5f) * 2f * PI.toFloat()
        val theta = v * PI.toFloat()
        return Vec3(sin(theta) * sin(phi), cos(theta), -sin(theta) * cos(phi))
    }
}
