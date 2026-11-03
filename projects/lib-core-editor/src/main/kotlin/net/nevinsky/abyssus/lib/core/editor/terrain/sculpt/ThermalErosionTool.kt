/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import kotlin.math.abs
import kotlin.math.tan

/** The default number of erosion passes per stamp. */
const val EROSION_ITERATIONS_DEFAULT = 8

/** The share of a pair's excess slope one pass moves downhill. */
private const val TRANSFER_SHARE = 0.5f

/**
 * Thermal erosion: moves material downhill between neighbours whose slope exceeds the [talusAngleDegrees], for
 * [iterations] passes over a copy of the footprint in a fixed row order, with no randomness. A pair moves material in
 * proportion to the smaller of its two brush weights, and gives to the lower vertex exactly what it takes from the higher,
 * so the height under the brush is conserved (to float tolerance) and the vertices outside the footprint stay put.
 */
class ThermalErosionTool(val talusAngleDegrees: Float, val iterations: Int = EROSION_ITERATIONS_DEFAULT) : SculptTool {
    init {
        require(talusAngleDegrees > 0f && talusAngleDegrees < 90f) { "talus angle must be between 0 and 90 degrees" }
        require(iterations >= 1) { "iterations must be at least 1" }
    }

    private val slope = tan(Math.toRadians(talusAngleDegrees.toDouble())).toFloat()

    override fun changes(stamp: SculptStamp, out: FloatArray) {
        val scratch = FootprintScratch(stamp)
        val w = scratch.width
        val allowedX = slope * stamp.cellWorldX
        val allowedZ = slope * stamp.cellWorldZ
        repeat(iterations) {
            for (z in 0 until scratch.height) for (x in 0 until w - 1) move(scratch, z * w + x, z * w + x + 1, allowedX)
            for (z in 0 until scratch.height - 1) for (x in 0 until w) move(scratch, z * w + x, (z + 1) * w + x, allowedZ)
        }
        val f = stamp.footprint
        for (i in 0 until f.count) {
            val at = scratch.indexOf(f.gx[i], f.gz[i])
            out[i] = scratch.work[at] - scratch.original[at]
        }
    }

    private fun move(s: FootprintScratch, a: Int, b: Int, allowed: Float) {
        val share = minOf(s.weight[a], s.weight[b])
        if (share <= 0f) return
        val d = s.work[a] - s.work[b]
        val excess = abs(d) - allowed
        if (excess <= 0f) return
        val moved = excess * TRANSFER_SHARE * share / 2f
        val sign = if (d > 0f) 1f else -1f
        s.work[a] -= sign * moved
        s.work[b] += sign * moved
    }
}
