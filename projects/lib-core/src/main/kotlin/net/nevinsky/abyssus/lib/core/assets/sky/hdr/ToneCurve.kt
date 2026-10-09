/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.hdr

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * How an HDR sky is shown: radiance times [exposure], the ACES filmic curve fitted by Narkowicz, clamped to 0..1, then
 * gamma 1/2.2. `/shader/sky/hdrsky.frag` uses the same constants.
 */
class ToneCurve(val exposure: Float = 1.0f) {
    fun aces(x: Float): Float = (x * (2.51f * x + 0.03f) / (x * (2.43f * x + 0.59f) + 0.14f)).coerceIn(0f, 1f)

    /** Display value 0..1 of [radiance]. */
    fun display(radiance: Float): Float = aces(maxOf(radiance, 0f) * exposure).pow(1f / 2.2f)

    /** Display value 0..255 of [radiance], rounded. */
    fun byte(radiance: Float): Int = (display(radiance) * 255f).roundToInt()
}
