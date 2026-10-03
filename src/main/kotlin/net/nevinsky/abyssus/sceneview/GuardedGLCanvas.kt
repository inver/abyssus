/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.diagnostic.thisLogger
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.Component
import java.awt.Frame
import java.awt.Graphics
import javax.swing.SwingUtilities

/**
 * Only touch GL while the surface is on screen, non-empty and its window is not minimized. On macOS a zero-sized
 * surface makes Metal reject the backing texture and abort the whole JVM.
 */
fun canRender(showing: Boolean, width: Int, height: Int, minimized: Boolean) =
    showing && width > 0 && height > 0 && !minimized

fun canRender(component: Component): Boolean {
    val frame = SwingUtilities.getWindowAncestor(component) as? Frame
    val minimized = frame != null && (frame.extendedState and Frame.ICONIFIED) != 0
    return canRender(component.isShowing, component.width, component.height, minimized)
}

/**
 * Opens only after the condition has held without interruption for [holdNanos]. AWT reports a new size before the
 * native macOS view has been resized (that happens asynchronously on the AppKit thread), so a size that is only just
 * non-zero can still be an empty surface natively.
 */
class StableGate(private val holdNanos: Long) {
    private var okSince = -1L

    fun update(ok: Boolean, nowNanos: Long): Boolean {
        if (!ok) {
            okSince = -1L
            return false
        }
        if (okSince < 0) okSince = nowNanos
        return nowNanos - okSince >= holdNanos
    }
}

/**
 * An [AWTGLCanvas] that refuses to run GL while its surface could be empty: in the timer ([glSafe]), in AWT repaints,
 * and when disposing the context (which makes it current). Shared by every GL canvas of the plugin.
 */
abstract class GuardedGLCanvas(data: GLData) : AWTGLCanvas(data) {
    private val gate = StableGate(STABLE_NANOS)

    /** True when GL may be used right now. Call before [render]. */
    fun glSafe(): Boolean = gate.update(canRender(this), System.nanoTime())

    override fun paint(g: Graphics) {
        if (glSafe()) super.paint(g)
    }

    override fun update(g: Graphics) {
        if (glSafe()) super.update(g)
    }

    /** Called when the GL context had to be dropped without being made current; drop any state that refers to it. */
    protected open fun onContextAbandoned() {}

    /**
     * Disposing makes the context current, which aborts the JVM on a hidden/empty surface. Then the context and its
     * native view are deleted without ever being made current (so GL objects in it cannot be released, but nothing is
     * left on screen).
     */
    override fun disposeCanvas() {
        if (context != 0L && !canRender(this)) {
            thisLogger().warn("GL canvas disposed while hidden; releasing its context without making it current")
            onContextAbandoned()
            // Leaving the native view and layer alive would leave the last frame on screen, in the old place, long
            // after the component is gone. Deleting the context tears them down and needs no current context.
            try {
                platformCanvas.deleteContext(context)
            } catch (e: Throwable) {
                thisLogger().warn("Failed to delete the GL context of a hidden canvas", e)
            }
            context = 0L
            initCalled = false
        }
        super.disposeCanvas()
    }

    private companion object {
        const val STABLE_NANOS = 250_000_000L
    }
}
