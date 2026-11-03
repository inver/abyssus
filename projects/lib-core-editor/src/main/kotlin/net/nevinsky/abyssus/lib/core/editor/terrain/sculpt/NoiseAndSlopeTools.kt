/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import net.nevinsky.abyssus.lib.core.editor.terrain.NoiseSampler
import net.nevinsky.abyssus.lib.core.editor.terrain.NoiseSamplerFactory
import kotlin.math.abs
import kotlin.math.tan

private const val NOISE_OCTAVES = 3
private const val NOISE_PERSISTENCE = 0.5f
private const val NOISE_LACUNARITY = 2f

/** The default relaxation passes Slope limit runs per stamp. */
const val SLOPE_ITERATIONS_DEFAULT = 8

/**
 * Noise: adds `amplitude * noise * weight` under the brush. The noise is sampled at the vertex's terrain-local
 * position (`x / (resolution - 1) * size`, like the generator), so the same position gets the same value at any
 * resolution and a repeated stroke gives identical heights.
 */
class NoiseTool(private val sampler: NoiseSampler, val amplitude: Float) : SculptTool {
    init {
        require(amplitude.isFinite()) { "amplitude must be finite" }
    }

    override fun changes(stamp: SculptStamp, out: FloatArray) {
        val grid = stamp.grid
        val step = grid.size.toDouble() / (grid.resolution - 1)
        val f = stamp.footprint
        for (i in 0 until f.count) {
            val n = sampler.sample((f.gx[i] * step).toFloat(), (f.gz[i] * step).toFloat())
            out[i] = amplitude * n * f.weight[i]
        }
    }
}

/** A [NoiseTool] whose noise has features about [featureSize] world units wide, from [seed] (the stroke's seed). */
fun noiseTool(factory: NoiseSamplerFactory, seed: Int, featureSize: Float, amplitude: Float): NoiseTool {
    require(featureSize.isFinite() && featureSize > 0f) { "feature size must be positive" }
    return NoiseTool(factory.create(seed, 1f / featureSize, NOISE_OCTAVES, NOISE_PERSISTENCE, NOISE_LACUNARITY), amplitude)
}

/**
 * Slope limit: lowers peaks and raises pits until no slope between neighbours under the brush exceeds [maxAngleDegrees],
 * as far as [iterations] relaxation passes get, then scales the result by the brush weight. The passes run over a copy of
 * the footprint in a fixed row order with the vertices outside the footprint held still, so the result is deterministic.
 */
class SlopeLimitTool(val maxAngleDegrees: Float, val iterations: Int = SLOPE_ITERATIONS_DEFAULT) : SculptTool {
    init {
        require(maxAngleDegrees > 0f && maxAngleDegrees < 90f) { "angle must be between 0 and 90 degrees" }
        require(iterations >= 1) { "iterations must be at least 1" }
    }

    private val slope = tan(Math.toRadians(maxAngleDegrees.toDouble())).toFloat()

    override fun changes(stamp: SculptStamp, out: FloatArray) {
        val scratch = FootprintScratch(stamp)
        val allowedX = slope * stamp.cellWorldX
        val allowedZ = slope * stamp.cellWorldZ
        val w = scratch.width
        val free = BooleanArray(scratch.weight.size) { scratch.weight[it] > 0f }
        repeat(iterations) {
            for (z in 0 until scratch.height) for (x in 0 until w - 1) relax(scratch.work, free, z * w + x, z * w + x + 1, allowedX)
            for (z in 0 until scratch.height - 1) for (x in 0 until w) relax(scratch.work, free, z * w + x, (z + 1) * w + x, allowedZ)
        }
        val f = stamp.footprint
        for (i in 0 until f.count) {
            val at = scratch.indexOf(f.gx[i], f.gz[i])
            out[i] = (scratch.work[at] - scratch.original[at]) * f.weight[i]
        }
    }

    /** Moves the pair [a],[b] together by the amount their height difference is above [allowed]; fixed vertices stay. */
    private fun relax(work: FloatArray, free: BooleanArray, a: Int, b: Int, allowed: Float) {
        if (!free[a] && !free[b]) return
        val d = work[a] - work[b]
        val excess = abs(d) - allowed
        if (excess <= 0f) return
        val sign = if (d > 0f) 1f else -1f
        if (free[a] && free[b]) {
            work[a] -= sign * excess / 2f
            work[b] += sign * excess / 2f
        } else if (free[a]) {
            work[a] -= sign * excess
        } else {
            work[b] += sign * excess
        }
    }
}
