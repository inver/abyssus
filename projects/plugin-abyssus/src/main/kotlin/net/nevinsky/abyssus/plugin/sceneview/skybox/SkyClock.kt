/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

/** The longest step one frame may advance the clock: a view that was hidden or paused resumes where it stopped. */
private const val MAX_STEP_SECONDS = 0.25f

/**
 * The time a view's sky is drawn at, in seconds since the view opened: clouds drift by it and sun occlusion follows
 * it. Advanced by the frame delta on the thread that renders; [fix] holds it at one time (for tests).
 */
class SkyClock {
    /** The current time in seconds. */
    var seconds = 0.0
        private set

    /** The step of the last [advance], in seconds; 0 while fixed. */
    var lastStep = 0f
        private set

    private var fixed = false

    /** Moves the clock on by [deltaSeconds] (clamped to a quarter of a second) unless it is fixed. */
    fun advance(deltaSeconds: Float) {
        lastStep = if (fixed || !deltaSeconds.isFinite()) 0f else deltaSeconds.coerceIn(0f, MAX_STEP_SECONDS)
        seconds += lastStep
    }

    /** Holds the clock at [time] seconds until [release]. */
    fun fix(time: Double) {
        seconds = time
        fixed = true
    }

    /** Lets the clock run again from where it was held. */
    fun release() {
        fixed = false
    }
}
