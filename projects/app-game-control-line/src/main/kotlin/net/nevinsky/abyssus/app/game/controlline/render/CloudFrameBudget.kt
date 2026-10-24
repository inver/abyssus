/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.simplerThan

/** A frame longer than this, in seconds, is over budget (30 frames a second). */
const val CLOUD_FRAME_BUDGET = 0.033f

/** How many seconds in a row the frames must be over budget before the sky gives up its technique. */
const val CLOUD_SLOW_SECONDS = 2f

/** A gap longer than this, in seconds, is a pause or a loading screen, not a slow frame. */
const val CLOUD_FRAME_GAP = 0.25f

/**
 * Watches the game's frame times while the sky draws clouds more costly than layered and says when they have held it over
 * [CLOUD_FRAME_BUDGET] for [CLOUD_SLOW_SECONDS] in a row. Gaps over [CLOUD_FRAME_GAP] neither count nor break the run.
 * The game keeps its own copy: it carries no editor code.
 */
class CloudFrameBudget {
    private var slowSeconds = 0f

    /** Takes one frame [interval] in seconds; true when the frames have been too slow for long enough. */
    fun frame(interval: Float, volumetric: Boolean): Boolean {
        if (!volumetric) {
            slowSeconds = 0f
            return false
        }
        if (!interval.isFinite() || interval <= 0f || interval > CLOUD_FRAME_GAP) {
            return false
        }
        if (interval <= CLOUD_FRAME_BUDGET) {
            slowSeconds = 0f
            return false
        }
        slowSeconds += interval
        if (slowSeconds < CLOUD_SLOW_SECONDS - 1e-4f) return false
        slowSeconds = 0f
        return true
    }
}

/**
 * Decides which cloud technique the game's sky steps down to: each frame it is told the technique drawn and the frame
 * time, and answers the next simpler technique when [CloudFrameBudget] says the game has been too slow for long enough.
 * Layered, or no clouds, is never stepped down from.
 */
class CloudStepDown(private val budget: CloudFrameBudget = CloudFrameBudget()) {
    fun frame(seconds: Float, drawn: CloudTechnique?): CloudTechnique? {
        val watched = drawn != null && simplerThan(drawn) != null
        if (!budget.frame(seconds, watched)) return null
        return drawn?.let(::simplerThan)
    }
}
