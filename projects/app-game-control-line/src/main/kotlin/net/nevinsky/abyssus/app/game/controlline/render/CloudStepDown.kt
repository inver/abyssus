/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudFrameBudget
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.simplerThan

/**
 * The game's whole-frame budget (s): the p99 frame time of the README's "Performance" table, `proposed` until the
 * maintainer approves it (OpenSpec change `improve-control-line-performance-stability`, design decision D13).
 */
const val FRAME_BUDGET_SECONDS = 0.0167f

/**
 * The frame interval the cloud step-down holds the game to: [FRAME_BUDGET_SECONDS] and a tenth for the jitter of a
 * vsynced, 60-frame interval. Volumetric clouds that keep the game below about 55 frames a second for two seconds step
 * down; the 30 frames a second of before let them hold the reference machine at 35 to 40 (evidence M2).
 */
const val CLOUD_STEP_DOWN_INTERVAL = FRAME_BUDGET_SECONDS * 1.1f

/**
 * Decides which cloud technique the game's sky steps down to: each frame it is told the technique drawn and the frame
 * time, and answers the next simpler technique when [CloudFrameBudget] says the game has been too slow for long enough.
 * Layered, or no clouds, is never stepped down from.
 */
class CloudStepDown(private val budget: CloudFrameBudget = CloudFrameBudget()) {
    /** The frame interval (s) over which frames count as slow. */
    val budgetSeconds: Float get() = budget.budgetSeconds

    fun frame(seconds: Float, drawn: CloudTechnique?): CloudTechnique? {
        val watched = drawn != null && simplerThan(drawn) != null
        if (!budget.frame(seconds, watched)) return null
        return drawn?.let(::simplerThan)
    }
}
