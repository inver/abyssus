/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.awt.GLData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * The shared GL canvas lifecycle in a real GL 3.2 core canvas: the hooks run in order, GL calls inside them work because
 * the context is current, a frame failure reaches the timer's handler, and release runs once with the context current.
 * Opens a window: runs only with `-Dabyssus.glTests=true`.
 */
class GlCanvasHostGlTest {
    private val data get() = GLData().apply {
        majorVersion = 3; minorVersion = 2; profile = GLData.Profile.CORE
        forwardCompatible = true; doubleBuffer = true; swapInterval = 0
    }

    private class Recorder(val frames: CountDownLatch, val failAt: Int = -1) : GlCanvasHooks {
        val events = ArrayList<String>()
        var glErrorInPaint = -1
        private var painted = 0

        override fun beforeCreate() { events += "beforeCreate" }
        override fun create() { events += "create" }
        override fun beforeFrame(canvas: GuardedGLCanvas) { events += "beforeFrame" }
        override fun paint(canvas: GuardedGLCanvas, ctx: GdxContext, frame: GdxFrame) {
            events += "paint"
            GL11.glClearColor(0f, 1f, 0f, 1f)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
            glErrorInPaint = GL11.glGetError()
            if (painted++ == failAt) error("frame failed")
        }
        override fun framePresented() { events += "framePresented"; frames.countDown() }
        override fun beforeRelease() { events += "beforeRelease" }
        override fun release() { events += "release" }
    }

    private fun runWindow(hooks: Recorder, failed: (Throwable) -> Unit, until: () -> Unit) {
        lateinit var window: JFrame
        lateinit var canvas: GuardedGLCanvas
        lateinit var timer: GlFrameTimer
        SwingUtilities.invokeAndWait {
            canvas = GlCanvasHost(hooks, "release failed").newCanvas(data)
            timer = GlFrameTimer({ canvas }, failed)
            window = JFrame("Abyssus GL host test").apply {
                contentPane.add(canvas)
                setSize(240, 180)
                isVisible = true
            }
            timer.start()
        }
        try {
            until()
        } finally {
            SwingUtilities.invokeAndWait { timer.stop(); canvas.disposeCanvas(); window.dispose() }
        }
    }

    @Test
    fun hooksRunInOrderAndReleaseRunsOnceWithTheContextCurrent() {
        assumeTrue(GlHarness.enabled)
        val hooks = Recorder(CountDownLatch(2))
        runWindow(hooks, { throw it }) { assertTrue(hooks.frames.await(30, TimeUnit.SECONDS)) }
        SwingUtilities.invokeAndWait { }
        assertEquals(listOf("beforeCreate", "create"), hooks.events.take(2))
        assertEquals(listOf("beforeFrame", "paint", "framePresented"), hooks.events.drop(2).take(3))
        assertEquals(0, hooks.glErrorInPaint)
        assertEquals(listOf("beforeRelease", "release"), hooks.events.takeLast(2))
        assertEquals(1, hooks.events.count { it == "release" })
    }

    @Test
    fun aThrowingFrameGoesToTheTimersHandler() {
        assumeTrue(GlHarness.enabled)
        val hooks = Recorder(CountDownLatch(1), failAt = 0)
        val failure = CountDownLatch(1)
        var cause: Throwable? = null
        runWindow(hooks, { cause = it; failure.countDown() }) { assertTrue(failure.await(30, TimeUnit.SECONDS)) }
        assertEquals("frame failed", cause?.message)
    }
}
