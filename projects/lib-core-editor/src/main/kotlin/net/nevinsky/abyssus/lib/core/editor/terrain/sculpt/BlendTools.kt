/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

/**
 * Smooth: moves each height toward the average of its (up to four) grid neighbours by the brush weight. Every
 * average reads the heights from before the stamp, so the result does not depend on the order vertices are visited in.
 */
class SmoothTool : SculptTool {
    override fun changes(stamp: SculptStamp, out: FloatArray) {
        val grid = stamp.grid
        val last = grid.resolution - 1
        val f = stamp.footprint
        for (i in 0 until f.count) {
            val x = f.gx[i]
            val z = f.gz[i]
            var sum = 0f
            var n = 0
            if (x > 0) {
                sum += grid.at(x - 1, z); n++
            }
            if (x < last) {
                sum += grid.at(x + 1, z); n++
            }
            if (z > 0) {
                sum += grid.at(x, z - 1); n++
            }
            if (z < last) {
                sum += grid.at(x, z + 1); n++
            }
            out[i] = (sum / n - grid.at(x, z)) * f.weight[i]
        }
    }
}

/**
 * Flatten: moves each height toward the height under the point where the stroke began. Written as a relative change, so
 * after Regenerate the pad follows the new noise (design decision 4).
 */
class FlattenTool : SculptTool {
    override fun changes(stamp: SculptStamp, out: FloatArray) = towards(stamp, stamp.strokeStart, out)
}

/** Set height: moves each height toward [target], a height typed into the strip, in world units. */
class SetHeightTool(val target: Float) : SculptTool {
    init {
        require(target.isFinite()) { "target height must be finite" }
    }

    override fun changes(stamp: SculptStamp, out: FloatArray) = towards(stamp, target, out)
}

private fun towards(stamp: SculptStamp, target: Float, out: FloatArray) {
    val f = stamp.footprint
    for (i in 0 until f.count) out[i] = (target - stamp.grid.at(f.gx[i], f.gz[i])) * f.weight[i]
}
