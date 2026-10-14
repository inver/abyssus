/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

/**
 * Frames per second of one viewport, measured over windows of at least a second from explicit monotonic timestamps (no
 * clock, no GL, no Swing). A window begins immediately before its first eligible frame; every frame that completed
 * (was swapped to the screen) counts. [display] is the last published whole number, or null before the first window ends
 * and after [reset]. A zero or backward timestamp never publishes: it restarts the window.
 */
class FpsCounter {
    /** The last published frames per second, or null while no window has ended since the start or the last [reset]. */
    var display: Int? = null
        private set

    private var windowStart = NONE
    private var frames = 0L

    /** Forgets the window and the published value (the view was disabled, hidden, resized to nothing, failed or re-created). */
    fun reset() {
        windowStart = NONE
        frames = 0
        display = null
    }

    /** A frame is about to be drawn at [nowNanos]; starts the window when none is running. */
    fun frameStarted(nowNanos: Long) {
        if (windowStart == NONE) {
            windowStart = nowNanos
            frames = 0
        }
    }

    /** A frame finished and was presented at [nowNanos]; publishes when the window is at least a second long. */
    fun frameCompleted(nowNanos: Long) {
        if (windowStart == NONE) return
        val elapsed = nowNanos - windowStart
        if (elapsed <= 0) {
            // a clock that stood still or went back: start over from here
            windowStart = nowNanos
            frames = 0
            return
        }
        frames++
        if (elapsed < WINDOW_NANOS) return
        display = Math.round(frames * NANOS_PER_SECOND / elapsed.toDouble()).toInt().coerceAtLeast(0)
        windowStart = nowNanos
        frames = 0
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val WINDOW_NANOS = 1_000_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
