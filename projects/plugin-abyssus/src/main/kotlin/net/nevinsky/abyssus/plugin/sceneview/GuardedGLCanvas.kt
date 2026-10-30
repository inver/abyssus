/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.diagnostic.thisLogger
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.Component
import java.awt.Frame
import java.awt.Graphics
import java.awt.image.BufferedImage
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

    /** Read the displayed frame before hiding the native surface for a Swing overlay. Never touch hidden GL. */
    internal fun captureFrame(prepare: () -> Unit): BufferedImage? {
        if (context == 0L || !glSafe()) return null
        return executeInContext {
            prepare()
            val w = framebufferWidth
            val h = framebufferHeight
            val pixels = MemoryUtil.memAlloc(w * h * 4)
            val framebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0)
            val readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER)
            val packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)
            val packSettings = intArrayOf(GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS)
            val packValues = packSettings.map { GL11.glGetInteger(it) }
            try {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
                packSettings.forEachIndexed { i, setting -> GL11.glPixelStorei(setting, if (i == 0) 1 else 0) }
                GL11.glReadBuffer(GL11.GL_FRONT)
                GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
                BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply {
                    val row = IntArray(w)
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            val i = ((h - y - 1) * w + x) * 4
                            row[x] = ((pixels[i].toInt() and 255) shl 16) or
                                ((pixels[i + 1].toInt() and 255) shl 8) or (pixels[i + 2].toInt() and 255)
                        }
                        setRGB(0, y, w, 1, row, 0, w)
                    }
                }
            } finally {
                packSettings.forEachIndexed { i, setting -> GL11.glPixelStorei(setting, packValues[i]) }
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer)
                GL11.glReadBuffer(readBuffer)
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer)
                MemoryUtil.memFree(pixels)
            }
        }
    }

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
