/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import net.nevinsky.abyssus.lib.core.editor.brush.BrushTarget
import net.nevinsky.abyssus.lib.core.editor.brush.ByteGrid
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CHANNELS = 4
private const val MAX_SUM = 255

/**
 * The [SplatDraft] as what a stroke changes. The four changes of a texel are rounded in magnitude (half up), applied
 * with their sign and clamped to 0 through 255; a texel whose bytes stay the same reports no change.
 *
 * With [exclusive] on, the channels that rose are applied first and, when the four weights then sum to more than 255,
 * the excess is taken from the channels that did not rise in proportion to their weights, with largest-remainder
 * integer rounding and ties broken by channel order, so the result is deterministic and the sum never exceeds 255. Only
 * when the others cannot give enough are the raised channels cut back the same way. Lowering never redistributes.
 */
class SplatTarget(private val draft: SplatDraft, private val exclusive: Boolean) : BrushTarget<ByteGrid> {
    override val grid: ByteGrid = draft.grid

    private val old = IntArray(CHANNELS)
    private val next = IntArray(CHANNELS)
    private val raised = BooleanArray(CHANNELS)

    override fun apply(gx: Int, gz: Int, changes: FloatArray, offset: Int): Boolean {
        val base = (gz * draft.resolution + gx) * CHANNELS
        val pixels = draft.pixels
        for (c in 0 until CHANNELS) {
            val was = pixels[base + c].toInt() and 0xFF
            val change = changes[offset + c]
            val delta = abs(change).roundToInt()
            old[c] = was
            next[c] = when {
                delta == 0 -> was
                change > 0f -> minOf(255, was + delta)
                else -> maxOf(0, was - delta)
            }
            raised[c] = next[c] > was
        }
        if (exclusive && raised.any { it }) redistribute()
        var differs = false
        for (c in 0 until CHANNELS) {
            if (next[c] != old[c]) {
                pixels[base + c] = next[c].toByte()
                differs = true
            }
        }
        if (differs) draft.touch(gx, gz)
        return differs
    }

    private fun redistribute() {
        var excess = next.sum() - MAX_SUM
        if (excess <= 0) return
        excess = take(excess, raisedChannels = false)
        if (excess > 0) take(excess, raisedChannels = true)
    }

    /** Takes [excess] from the channels that did (or did not) rise, proportionally; returns what could not be taken. */
    private fun take(excess: Int, raisedChannels: Boolean): Int {
        var total = 0
        for (c in 0 until CHANNELS) if (raised[c] == raisedChannels) total += next[c]
        if (total == 0) return excess
        if (total <= excess) {
            for (c in 0 until CHANNELS) if (raised[c] == raisedChannels) next[c] = 0
            return excess - total
        }
        // floor of each proportional share, then one more to the channels with the largest remainders (ties: lowest first)
        var taken = 0
        val remainder = IntArray(CHANNELS) { -1 }
        for (c in 0 until CHANNELS) {
            if (raised[c] != raisedChannels || next[c] == 0) continue
            val exact = excess * next[c]
            val share = exact / total
            remainder[c] = exact % total
            next[c] -= share
            taken += share
        }
        repeat(excess - taken) {
            var best = -1
            for (c in 0 until CHANNELS) if (remainder[c] >= 0 && (best < 0 || remainder[c] > remainder[best])) best = c
            next[best]--
            remainder[best] = -1
        }
        return 0
    }
}
