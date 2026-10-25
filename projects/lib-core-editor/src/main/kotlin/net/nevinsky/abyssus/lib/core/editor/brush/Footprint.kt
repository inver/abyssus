/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.brush

private const val INITIAL = 256

/**
 * The grid cells within one stamp's radius. Each has its [falloff], `1 - d / radius` from 0 at the radius to 1 at the
 * centre, and its brush [weight], which a [BrushTool] without its own curve takes as the strength times the smooth
 * falloff. The arrays grow by doubling and are reused from stamp to stamp, so a drag allocates nothing once it has
 * seen its largest stamp.
 */
class Footprint {
    var count: Int = 0
        private set
    var gx: IntArray = IntArray(INITIAL)
        private set
    var gz: IntArray = IntArray(INITIAL)
        private set
    var falloff: FloatArray = FloatArray(INITIAL)
        private set
    var weight: FloatArray = FloatArray(INITIAL)
        private set

    fun clear() {
        count = 0
    }

    fun add(x: Int, z: Int, falloff: Float, weight: Float) {
        if (count == gx.size) {
            gx = gx.copyOf(count * 2)
            gz = gz.copyOf(count * 2)
            this.falloff = this.falloff.copyOf(count * 2)
            this.weight = this.weight.copyOf(count * 2)
        }
        gx[count] = x
        gz[count] = z
        this.falloff[count] = falloff
        this.weight[count] = weight
        count++
    }
}
