/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

/** The default world units one full-weight stamp raises a vertex by. */
const val RAISE_RATE_DEFAULT = 0.5f

/** Raise/Lower: adds `rate * weight` under the brush, and subtracts it while the brush is inverted (Shift). */
class RaiseLowerTool(val rate: Float = RAISE_RATE_DEFAULT) : SculptTool {
    override fun changes(stamp: SculptStamp, out: FloatArray) {
        val sign = if (stamp.inverted) -1f else 1f
        val f = stamp.footprint
        for (i in 0 until f.count) out[i] = sign * rate * f.weight[i]
    }
}
