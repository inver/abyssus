/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

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

    /** A frame is about to start, before its clock ticks. */
    fun frameStarting(canvas: GuardedGLCanvas)

    /** Draws one frame into [canvas] with [ctx] current-capable and [frame] ticked; the lifecycle swaps buffers afterwards. */
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
    private val host = GlCanvasHost(object : GlCanvasHooks {
        // Even a failure in capability/resource creation is a renderer error, not a refused pixel format.
        override fun beforeCreate() { contextInitialized = true }

        override fun create() = renderer.create()

        override fun beforeFrame(canvas: GuardedGLCanvas) = hooks.frameStarting(canvas)

        override fun paint(canvas: GuardedGLCanvas, ctx: GdxContext, frame: GdxFrame) = hooks.paintFrame(canvas, ctx, frame)

        override fun framePresented() = hooks.framePresented()

        override fun beforeRelease() = hooks.beforeDispose()

        override fun release() { hooks.disposeInContext(); renderer.dispose() }

        override fun abandoned() { hooks.abandoned(); renderer.abandonShadows() }

        override fun contextLost() = hooks.contextLost()
    }, "Failed to release scene view GL resources")

    /** The capabilities of the current context; the canvas capture re-binds them before reading pixels. */
    val capabilities: GLCapabilities? get() = host.capabilities

    /** True once the current canvas ran `initGL`: a failure after that is a renderer error, not a refused pixel format. */
    var contextInitialized = false

    fun newCanvas(samples: Int, swapControl: Boolean = true): GuardedGLCanvas = host.newCanvas(glData(samples, swapControl))

    private companion object {
        fun glData(sampleCount: Int, swapControl: Boolean) = GLData().apply {
            samples = sampleCount
            sampleBuffers = if (sampleCount > 0) 1 else 0
            majorVersion = 3
            // instanced foliage shadows call glVertexAttribDivisor, which is core only from 3.3; a context made as exactly 3.2 aborts the JVM on it
            minorVersion = 3
            profile = GLData.Profile.CORE
            forwardCompatible = true
            depthSize = 24
            // the Swing timer paces frames; a vsync-blocked swap would stall the IDE thread. Left unset when the GLX server has no swap control.
            swapInterval = if (swapControl) 0 else null
        }
    }
}
