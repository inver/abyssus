/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import net.nevinsky.abyssus.lib.core.editor.brush.BrushStamp
import net.nevinsky.abyssus.lib.core.editor.brush.BrushTool
import net.nevinsky.abyssus.lib.core.editor.brush.ByteGrid
import net.nevinsky.abyssus.lib.core.editor.brush.smoothFalloff

/** The falloff units of one stamp, as in Paint Foliage: strength 1 reaches full weight in a few stamps. */
private const val BRUSH_UNITS: Float = 64f

/**
 * What one splat tool does to the weights under a stamp (Strategy): four changes per footprint texel, one per channel,
 * written into `out[i * 4 + c]`. The [SplatTarget] rounds, clamps and, when Exclusive, renormalizes them.
 */
sealed interface SplatTool : BrushTool<ByteGrid> {
    override val channels: Int get() = 4

    /**
     * Paint: raises the weight of [slot]'s channel by `strength * 64 * smoothstep(1 - d / radius)`; reversed (Shift)
     * lowers that channel only. Painting the Base slot (0) lowers every channel, as [Erase] does.
     */
    class Paint(val slot: Int) : SplatTool {
        init {
            require(slot in 0 until SPLAT_SLOTS) { "slot $slot" }
        }

        override fun changes(stamp: BrushStamp<ByteGrid>, out: FloatArray) {
            val f = stamp.footprint
            for (i in 0 until f.count) {
                val delta = stamp.strength * BRUSH_UNITS * smoothFalloff(f.falloff[i])
                if (slot == 0) {
                    for (c in 0 until 4) out[i * 4 + c] = -delta
                } else {
                    out[i * 4 + slot - 1] = if (stamp.reversed) -delta else delta
                }
            }
        }
    }

    /** Erase: lowers every channel, revealing the Base layer. */
    data object Erase : SplatTool {
        override fun changes(stamp: BrushStamp<ByteGrid>, out: FloatArray) {
            val f = stamp.footprint
            for (i in 0 until f.count) {
                val delta = stamp.strength * BRUSH_UNITS * smoothFalloff(f.falloff[i])
                for (c in 0 until 4) out[i * 4 + c] = -delta
            }
        }
    }

    /**
     * Smooth: moves each channel toward the average of its (up to nine) 3x3 neighbours by the brush weight. The
     * stroke computes every change before it applies any, so the averages read the weights from before the stamp.
     */
    data object Smooth : SplatTool {
        override fun changes(stamp: BrushStamp<ByteGrid>, out: FloatArray) {
            val grid = stamp.grid
            val last = grid.resolution - 1
            val f = stamp.footprint
            for (i in 0 until f.count) {
                val x = f.gx[i]
                val z = f.gz[i]
                for (c in 0 until 4) {
                    var sum = 0
                    var n = 0
                    for (nz in maxOf(0, z - 1)..minOf(last, z + 1)) {
                        for (nx in maxOf(0, x - 1)..minOf(last, x + 1)) {
                            sum += grid.at(nx, nz, c)
                            n++
                        }
                    }
                    out[i * 4 + c] = (sum.toFloat() / n - grid.at(x, z, c)) * f.weight[i]
                }
            }
        }
    }
}
