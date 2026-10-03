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

import kotlin.math.pow

/**
 * How an HDR sky is shown: radiance times [EXPOSURE], the ACES filmic curve fitted by Narkowicz, clamped to 0..1, then
 * gamma 1/2.2. `/shader/scene/hdrsky.frag` uses the same constants.
 */
object HdrToneMap {
    const val EXPOSURE = 1.0f

    fun aces(x: Float): Float = (x * (2.51f * x + 0.03f) / (x * (2.43f * x + 0.59f) + 0.14f)).coerceIn(0f, 1f)

    /** Display value 0..1 of [radiance]. */
    fun display(radiance: Float): Float = aces(maxOf(radiance, 0f) * EXPOSURE).pow(1f / 2.2f)

    /** Display value 0..255 of [radiance], rounded. */
    fun byte(radiance: Float): Int = Math.round(display(radiance) * 255f)
}
