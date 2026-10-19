/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.progress.ProcessCanceledException
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.util.concurrent.CancellationException
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.Timer
import java.awt.event.InputEvent

/**
 * Paces a view's frames: a Swing timer renders at the full rate for [IDLE_AFTER_NANOS] after input on [owner] or while
 * [busy], and otherwise every [IDLE_FRAME_MILLIS]. A frame is skipped while the canvas is not [glSafe]. EDT only.
 */
internal class SceneFrameDriver(
    private val owner: JComponent,
    /** The canvas to draw; read on every tick, because anti-aliasing replaces it. */
    private val canvas: () -> GuardedGLCanvas,
    /** True while something moves or may change at once, beyond recent input. */
    private val busy: () -> Boolean,
    /** The surface cannot be drawn on: the frame-rate sample starts fresh. */
    private val interrupted: () -> Unit,
    /** Rendering threw; cancellation is rethrown before this runs. */
    private val renderFailed: (Throwable) -> Unit,
) {
    private var lastActivityNanos = System.nanoTime()
    private var lastRenderNanos = 0L

    /** Marks input on [owner]. */
    private val activityListener = AWTEventListener { event ->
        if (event is InputEvent && event.source.let { it is Component && SwingUtilities.isDescendingFrom(it, owner) }) {
            lastActivityNanos = System.nanoTime()
        }
    }

    private val timer = Timer(FRAME_MILLIS) {
        if (!canvas().glSafe()) { interrupted(); return@Timer }
        val now = System.nanoTime()
        if (!isBusy(now) && now - lastRenderNanos < IDLE_FRAME_MILLIS * 1_000_000L) return@Timer
        lastRenderNanos = now
        try {
            canvas().render()
        } catch (e: Throwable) {
            if (e is ProcessCanceledException || e is CancellationException) throw e
            interrupted()
            renderFailed(e)
        }
    }

    private fun isBusy(now: Long) = now - lastActivityNanos < IDLE_AFTER_NANOS || busy()

    /** Starts the frame loop and the input watch; the view is on screen. */
    fun start() {
        lastActivityNanos = System.nanoTime()
        Toolkit.getDefaultToolkit().addAWTEventListener(
            activityListener,
            AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or
                AWTEvent.MOUSE_WHEEL_EVENT_MASK or AWTEvent.KEY_EVENT_MASK,
        )
        timer.start()
    }

    /** Stops the frame loop; the input watch stays until [release]. */
    fun stop() = timer.stop()

    /** Stops the loop and drops the input watch; the view left the screen. */
    fun release() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener)
        stop()
    }

    private companion object {
        const val FRAME_MILLIS = 16

        /** An idle view (no input for [IDLE_AFTER_NANOS], nothing loading or playing) renders every this many ms. */
        const val IDLE_FRAME_MILLIS = 100
        const val IDLE_AFTER_NANOS = 1_000_000_000L
    }
}
