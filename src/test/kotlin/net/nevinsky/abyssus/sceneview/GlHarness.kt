/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.editor.pick.OrbitCamera
import com.badlogic.gdx.backends.lwjgl3.GdxGlBridge
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.BorderLayout
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Renders a scene through the real [SceneRenderer] into a real GL 3.2 core canvas. Opens a window, so the tests using
 * it only run with `-Dabyssus.glTests=true` (and a display).
 */
object GlHarness {
    val enabled: Boolean get() = System.getProperty("abyssus.glTests") == "true" && !GraphicsEnvironment.isHeadless()

    class Result(val image: BufferedImage, val error: Throwable?)

    /** Runs [frames] frames; [afterFrame] runs on the AWT thread inside the GL context after each; returns the last frame. */
    fun render(
        params: SceneRenderParams,
        frames: Int,
        orbit: OrbitCamera = OrbitCamera(params.camera),
        executor: java.util.concurrent.Executor? = null,
        framebufferSize: Pair<Int, Int>? = null,
        afterFrame: (SceneRenderer, Int) -> Unit = { _, _ -> },
    ): Result {
        val done = CountDownLatch(1)
        var image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        var error: Throwable? = null
        val pool = Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } }
        val frame = GdxFrame()
        val renderer = testRenderer(executor ?: pool).also { it.params = params }
        var count = 0
        lateinit var timer: Timer
        lateinit var window: JFrame
        SwingUtilities.invokeAndWait {
            val data = GLData().apply {
                majorVersion = 3; minorVersion = 2; profile = GLData.Profile.CORE; forwardCompatible = true; depthSize = 24; swapInterval = 0
            }
            var ctx: GdxContext? = null
            val canvas = object : AWTGLCanvas(data) {
                override fun initGL() {
                    GL.createCapabilities()
                    ctx = GdxRuntime.newContext(frame, GdxGlBridge.gl20(), GdxGlBridge.gl30(), Lwjgl3Files())
                    GdxRuntime.withContext(ctx!!) { renderer.create() }
                }

                override fun paintGL() {
                    frame.tick(framebufferWidth, framebufferHeight)
                    GdxRuntime.withContext(ctx!!) {
                        renderer.render(frame.width, frame.height, orbit, frame.deltaSeconds)
                        afterFrame(renderer, count)
                    }
                    count++
                    if (count == frames) image = readPixels(framebufferWidth, framebufferHeight)
                    swapBuffers()
                }
            }
            window = JFrame("abyssus GL test").apply {
                layout = BorderLayout()
                add(canvas, BorderLayout.CENTER)
                if (framebufferSize == null) setSize(640, 400)
                else {
                    val scale = graphicsConfiguration.defaultTransform
                    canvas.preferredSize = java.awt.Dimension(
                        (framebufferSize.first / scale.scaleX).toInt(), (framebufferSize.second / scale.scaleY).toInt()
                    )
                    pack()
                }
                isVisible = true
            }
            timer = Timer(16) {
                try {
                    canvas.render()
                } catch (e: Throwable) {
                    error = e
                    e.printStackTrace()
                    count = frames
                }
                if (count >= frames) {
                    timer.stop()
                    try {
                        ctx?.let { GdxRuntime.withContext(it) { renderer.dispose() } }
                    } catch (e: Throwable) {
                        error = error ?: e
                    }
                    window.dispose()
                    done.countDown()
                }
            }
            timer.start()
        }
        check(done.await(60, TimeUnit.SECONDS)) { "GL test timed out after $count frames" }
        return Result(image, error)
    }

    private fun readPixels(w: Int, h: Int): BufferedImage {
        val buf = ByteBuffer.allocateDirect(w * h * 4)
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) {
            val i = ((h - 1 - y) * w + x) * 4
            img.setRGB(x, y, ((buf[i].toInt() and 255) shl 16) or ((buf[i + 1].toInt() and 255) shl 8) or (buf[i + 2].toInt() and 255))
        }
        return img
    }

    fun save(image: BufferedImage, file: java.io.File) {
        ImageIO.write(image, "png", file)
    }

    /** The share of pixels that differ from the clear color of [params]. */
    fun coverage(image: BufferedImage, params: SceneRenderParams): Double {
        val c = params.clear
        val clear = (Math.round(c.r * 255) shl 16) or (Math.round(c.g * 255) shl 8) or Math.round(c.b * 255)
        var different = 0
        for (y in 0 until image.height) for (x in 0 until image.width) if (image.getRGB(x, y) and 0xFFFFFF != clear) different++
        return different.toDouble() / (image.width * image.height)
    }
}
