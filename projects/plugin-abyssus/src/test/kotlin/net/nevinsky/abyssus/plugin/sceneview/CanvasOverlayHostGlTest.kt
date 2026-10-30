/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.awt.GLData
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import javax.swing.Timer

class CanvasOverlayHostGlTest {
    @Test fun realCanvasSnapshotPreservesFrameAndLiveRenderingResumes() {
        assumeTrue(GlHarness.enabled)
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        lateinit var window: JFrame
        lateinit var render: Timer
        lateinit var canvas: GuardedGLCanvas
        SwingUtilities.invokeAndWait {
            var capabilities: GLCapabilities? = null
            var initializations = 0
            canvas = object : GuardedGLCanvas(GLData().apply {
                majorVersion = 3; minorVersion = 2; profile = GLData.Profile.CORE
                forwardCompatible = true; doubleBuffer = true; swapInterval = 0
            }) {
                override fun initGL() { initializations++; capabilities = GL.createCapabilities() }
                override fun paintGL() {
                    GL.setCapabilities(capabilities)
                    GL11.glViewport(0, 0, framebufferWidth, framebufferHeight)
                    GL11.glClearColor(1f, 0f, 0f, 1f)
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
                    swapBuffers()
                }
            }
            val host = CanvasOverlayHost(canvas) { canvas.captureFrame { GL.setCapabilities(capabilities) } }
            window = JFrame("Abyssus notification layering test").apply {
                contentPane.add(host)
                setSize(320, 240)
                isVisible = true
            }
            val balloon = JPanel().apply {
                background = Color.BLUE
                setBounds(80, 80, 160, 100)
            }
            var frames = 0
            var phase = 0
            render = Timer(30) {
                try {
                    if (!canvas.glSafe()) return@Timer
                    canvas.render()
                    frames++
                    if (phase == 0 && frames == 3) {
                        window.layeredPane.add(balloon, JLayeredPane.POPUP_LAYER, 0)
                        host.syncOverlay()
                        assertFalse(canvas.isShowing)
                        val image = BufferedImage(host.width, host.height, BufferedImage.TYPE_INT_RGB)
                        val graphics = image.createGraphics()
                        try { host.paint(graphics) } finally { graphics.dispose() }
                        assertEquals("The last GL frame remains visible through Swing", Color.RED.rgb, image.getRGB(20, 20))
                        val frame = BufferedImage(window.layeredPane.width, window.layeredPane.height, BufferedImage.TYPE_INT_RGB)
                        val frameGraphics = frame.createGraphics()
                        try { window.layeredPane.paint(frameGraphics) } finally { frameGraphics.dispose() }
                        assertEquals("Balloon paints above the snapshot", Color.BLUE.rgb, frame.getRGB(120, 120))
                        assertSame(balloon, SwingUtilities.getDeepestComponentAt(window.layeredPane, 120, 120))
                        window.layeredPane.remove(balloon)
                        host.syncOverlay()
                        assertTrue(canvas.isShowing)
                        phase = 1
                    } else if (phase == 1) {
                        assertEquals("Hiding retains the GL context", 1, initializations)
                        phase = 2
                        render.stop()
                        done.countDown()
                    }
                } catch (e: Throwable) {
                    failure = e
                    render.stop()
                    done.countDown()
                }
            }
            render.start()
        }
        try {
            assertTrue("GL overlay test timed out", done.await(30, TimeUnit.SECONDS))
            failure?.let { throw AssertionError("GL overlay test failed", it) }
        } finally {
            SwingUtilities.invokeAndWait {
                render.stop()
                canvas.disposeCanvas()
                window.dispose()
            }
        }
    }
}
