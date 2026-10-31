/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.backends.lwjgl3.GdxGlBridge
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.intellij.openapi.diagnostic.thisLogger
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.awt.GLData
import javax.swing.Timer

/** What a GL view does with its context; [GlCanvasHost] runs these in the right place. All on the AWT thread. */
internal interface GlCanvasHooks {
    /** Before the GL capabilities are made, so a failure in them can be told from a refused pixel format. */
    fun beforeCreate() {}

    /** The new libGDX context is current: make the view's GL resources. */
    fun create() {}

    /** Before a frame's clock ticks and the context is entered. */
    fun beforeFrame(canvas: GuardedGLCanvas) {}

    /** Draws one frame with the capabilities set and the clock ticked; the host swaps the buffers afterwards. */
    fun paint(canvas: GuardedGLCanvas, ctx: GdxContext, frame: GdxFrame)

    /** After the buffers were swapped. */
    fun framePresented() {}

    /** The context is about to be made current to be released; runs before that. */
    fun beforeRelease() {}

    /** The context is current and about to be released: free the view's GL resources. */
    fun release() {}

    /** The context is gone without having been made current: drop what depended on it, without freeing GL objects. */
    fun abandoned() {}

    /** After [abandoned] and the host's own cleanup: the canvas can be replaced. */
    fun contextLost() {}
}

/**
 * The GL context lifecycle every GL canvas of the plugin shares: creating the capabilities and the libGDX context, running
 * the view's create, draw and release inside `GdxRuntime.withContext` and only while the canvas is safe, and the abandoned
 * path. It owns the context, the capabilities and the frame clock; the view keeps its own CPU state across canvases made
 * by [newCanvas]. EDT only.
 */
internal class GlCanvasHost(
    private val hooks: GlCanvasHooks,
    /** Logged, with the cause, when releasing the GL resources throws. */
    private val releaseFailure: String,
) {
    val frame = GdxFrame()
    private var gdx: GdxContext? = null

    /** The capabilities of the current context; the canvas capture re-binds them before reading pixels. */
    var capabilities: GLCapabilities? = null
        private set

    fun newCanvas(data: GLData): GuardedGLCanvas = object : GuardedGLCanvas(data) {
        override fun initGL() {
            hooks.beforeCreate()
            capabilities = GL.createCapabilities()
            val ctx = GdxRuntime.newContext(frame, GdxGlBridge.gl20(), GdxGlBridge.gl30(), Lwjgl3Files())
            gdx = ctx
            GdxRuntime.withContext(ctx) { hooks.create() }
        }

        /** Runs once per GL context, with it current: on `removeNotify` and on `disposeCanvas`. */
        override fun disposeGL() {
            val ctx = gdx ?: return
            gdx = null
            capabilities?.let { GL.setCapabilities(it) }
            try {
                hooks.beforeRelease()
                GdxRuntime.withContext(ctx) { hooks.release() }
            } catch (e: Throwable) {
                thisLogger().warn(releaseFailure, e)
            }
        }

        override fun onContextAbandoned() {
            hooks.abandoned()
            gdx = null
            hooks.contextLost()
        }

        override fun paintGL() {
            val ctx = gdx ?: return
            capabilities?.let { GL.setCapabilities(it) }
            hooks.beforeFrame(this)
            frame.tick(framebufferWidth, framebufferHeight)
            hooks.paint(this, ctx, frame)
            swapBuffers()
            hooks.framePresented()
        }
    }
}

/** Renders [canvas] every [FRAME_MILLIS] while it is [GuardedGLCanvas.glSafe]; a throwing frame goes to [failed] (the timer keeps running unless it stops it). */
internal class GlFrameTimer(canvas: () -> GuardedGLCanvas, failed: (Throwable) -> Unit) {
    private val timer: Timer = Timer(FRAME_MILLIS) {
        val c = canvas()
        if (!c.glSafe()) return@Timer
        try {
            c.render()
        } catch (e: Throwable) {
            failed(e)
        }
    }

    fun start() = timer.start()

    fun stop() = timer.stop()

    private companion object {
        const val FRAME_MILLIS = 16
    }
}
