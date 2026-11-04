/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

/** A frame slower than this, in seconds, is over budget, unless a view passes its own budget. */
const val CLOUD_FRAME_BUDGET = 0.033f

/** How long, in seconds, frames must stay over budget before volumetric clouds fall back. */
const val CLOUD_SLOW_SECONDS = 2f

/** A gap between frames longer than this, in seconds, is a hidden, paused or loading view, not a slow frame. */
const val CLOUD_FRAME_GAP = 0.25f

/**
 * Watches a view's frame intervals (the wall time between two rendered frames) while it draws volumetric clouds and
 * reports when they have stayed over [budgetSeconds] (the editor's [CLOUD_FRAME_BUDGET], or a game's own frame budget)
 * for [CLOUD_SLOW_SECONDS] in a row. Gaps over [CLOUD_FRAME_GAP] neither count nor break the run. Pure; one thread.
 */
class CloudFrameBudget(val budgetSeconds: Float = CLOUD_FRAME_BUDGET) {
    init {
        require(budgetSeconds > 0f && budgetSeconds < CLOUD_FRAME_GAP) { "a frame budget of $budgetSeconds s" }
    }

    private var slowSeconds = 0f

    /**
     * Takes one frame [interval] in seconds; [volumetric] says whether volumetric clouds were drawn. True when the
     * view has been too slow for long enough, after which it starts counting again.
     */
    fun frame(interval: Float, volumetric: Boolean): Boolean {
        if (!volumetric) {
            reset()
            return false
        }
        if (!interval.isFinite() || interval <= 0f || interval > CLOUD_FRAME_GAP) return false
        if (interval <= budgetSeconds) {
            slowSeconds = 0f
            return false
        }
        slowSeconds += interval
        if (slowSeconds < CLOUD_SLOW_SECONDS - 1e-4f) return false // forty 50 ms frames add up to just under 2 in floats
        reset()
        return true
    }

    fun reset() {
        slowSeconds = 0f
    }
}
