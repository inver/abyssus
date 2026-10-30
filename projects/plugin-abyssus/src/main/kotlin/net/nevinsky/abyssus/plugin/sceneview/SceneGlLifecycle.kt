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

/** What the view does around the GL lifecycle that is not GL: ray feed, experiment, frame-rate sample and drawing. */
internal interface SceneGlHooks {
    /** The context is about to be released; runs before the context is made current for disposal. */
    fun beforeDispose()

    /** The context is current and about to be released. */
    fun disposeInContext()

    /** The context is gone and cannot be used to release anything; the view drops what depended on it. */
    fun abandoned()

    /** After [abandoned] and the renderer's own cleanup: the canvas can be replaced. */
    fun contextLost()

    /** Draws one frame into [canvas] with [ctx] current-capable; the lifecycle swaps buffers afterwards. */
    fun paintFrame(canvas: GuardedGLCanvas, ctx: GdxContext, frame: GdxFrame)

    /** The frame was presented (after the buffer swap). */
    fun framePresented()
}

/**
 * Creates the view's GL canvases and owns what lives and dies with one GL context: the libGDX context, the GL
 * capabilities and the frame clock. The view keeps its CPU state across canvas replacements. EDT only.
 */
internal class SceneGlLifecycle(
    private val renderer: SceneRenderer,
    private val hooks: SceneGlHooks,
) {
    private val frame = GdxFrame()
    private var gdx: GdxContext? = null

    /** The capabilities of the current context; the canvas capture re-binds them before reading pixels. */
    var capabilities: GLCapabilities? = null
        private set

    /** True once the current canvas ran `initGL`: a failure after that is a renderer error, not a refused pixel format. */
    var contextInitialized = false

    fun newCanvas(samples: Int): GuardedGLCanvas = object : GuardedGLCanvas(glData(samples)) {
        override fun initGL() {
            // Even a failure in capability/resource creation is a renderer error, not a refused pixel format.
            contextInitialized = true
            capabilities = GL.createCapabilities()
            val ctx = GdxRuntime.newContext(frame, GdxGlBridge.gl20(), GdxGlBridge.gl30(), Lwjgl3Files())
            gdx = ctx
            GdxRuntime.withContext(ctx) { renderer.create() }
        }

        /** Runs once per GL context, with it current: on `removeNotify` and on `disposeCanvas`. */
        override fun disposeGL() {
            val ctx = gdx ?: return
            gdx = null
            capabilities?.let { GL.setCapabilities(it) }
            try {
                hooks.beforeDispose()
                GdxRuntime.withContext(ctx) { hooks.disposeInContext(); renderer.dispose() }
            } catch (e: Throwable) {
                thisLogger().warn("Failed to release scene view GL resources", e)
            }
        }

        override fun onContextAbandoned() {
            hooks.abandoned()
            renderer.abandonShadows()
            gdx = null
            hooks.contextLost()
        }

        override fun paintGL() {
            val ctx = gdx ?: return
            capabilities?.let { GL.setCapabilities(it) }
            hooks.paintFrame(this, ctx, frame)
            swapBuffers()
            hooks.framePresented()
        }
    }

    private companion object {
        fun glData(sampleCount: Int) = GLData().apply {
            samples = sampleCount
            sampleBuffers = if (sampleCount > 0) 1 else 0
            majorVersion = 3
            // instanced foliage shadows call glVertexAttribDivisor, which is core only from 3.3; a context made as exactly 3.2 aborts the JVM on it
            minorVersion = 3
            profile = GLData.Profile.CORE
            forwardCompatible = true
            depthSize = 24
            swapInterval = 0 // the Swing timer paces frames; a vsync-blocked swap would stall the IDE thread
        }
    }
}
