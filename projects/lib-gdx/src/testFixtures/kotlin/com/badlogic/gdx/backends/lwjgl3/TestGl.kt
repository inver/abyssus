/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package com.badlogic.gdx.backends.lwjgl3

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.GdxNativesLoader
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.GraphicsEnvironment
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Runs a block once with a GL 3.3 core context current (the game's own minimum: instancing needs `glVertexAttribDivisor`) and libGDX's `Gdx.gl*` installed. Opens a small window, so
 * tests using it only run with `-Dabyssus.glTests=true` (and a display). In libGDX's package because `Lwjgl3GL30` is
 * package-private.
 */
object TestGl {
    /** The default size of the test window, and so of the default framebuffer the tests can draw into. */
    const val WINDOW_WIDTH = 320
    const val WINDOW_HEIGHT = 240

    /** The size of the window of the run in progress: what the stubbed back buffer reports. */
    @Volatile
    private var width = WINDOW_WIDTH

    @Volatile
    private var height = WINDOW_HEIGHT

    val enabled: Boolean get() = System.getProperty("abyssus.glTests") == "true" && !GraphicsEnvironment.isHeadless()

    fun <T> run(block: () -> T): T = run(30, block)

    /** [timeoutSeconds] bounds the whole block; a measurement run needs far more than the default. */
    fun <T> run(timeoutSeconds: Long, block: () -> T): T = run(timeoutSeconds, WINDOW_WIDTH, WINDOW_HEIGHT, block)

    /** As above in a window whose default framebuffer is [windowWidth] x [windowHeight] pixels (a measurement draws at its own resolution). */
    fun <T> run(timeoutSeconds: Long, windowWidth: Int, windowHeight: Int, block: () -> T): T {
        width = windowWidth
        height = windowHeight
        GdxNativesLoader.load()
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        lateinit var window: JFrame
        SwingUtilities.invokeAndWait {
            val data = GLData().apply {
                majorVersion = 3; minorVersion = 3; profile = GLData.Profile.CORE; forwardCompatible = true; depthSize = 24
            }
            val canvas = object : AWTGLCanvas(data) {
                override fun initGL() {
                    GL.createCapabilities()
                    install()
                }

                override fun paintGL() {
                    if (result == null) {
                        result = runCatching(block)
                        done.countDown()
                    }
                    swapBuffers()
                }
            }
            window = JFrame("gdx-model GL test").apply {
                canvas.preferredSize = java.awt.Dimension(windowWidth, windowHeight)
                add(canvas)
                pack()
                isVisible = true
            }
            // the canvas has a context only once it is shown: render from a later event
            javax.swing.Timer(50) { e -> if (result == null) canvas.render() else (e.source as javax.swing.Timer).stop() }.start()
        }
        check(done.await(timeoutSeconds, TimeUnit.SECONDS)) { "GL test timed out" }
        SwingUtilities.invokeLater { window.dispose() }
        return result!!.getOrThrow()
    }

    private fun install() {
        val gl = object : Lwjgl3GL30() {
            override fun glGenerateMipmap(target: Int) = GL30C.glGenerateMipmap(target)
        }
        Gdx.gl = gl
        Gdx.gl20 = gl
        Gdx.gl30 = gl
        val graphics = stub(Graphics::class.java) { name ->
            when (name) {
                "getDeltaTime" -> 0f
                "isGL30Available" -> true
                // libGDX's FrameBuffer.end() sets the viewport to the back buffer: a 0 x 0 one would clip everything drawn after it
                "getWidth", "getBackBufferWidth" -> width
                "getHeight", "getBackBufferHeight" -> height
                "getGL20", "getGL30" -> gl
                else -> null
            }
        }
        Gdx.graphics = graphics
        Gdx.app = stub(Application::class.java) { name ->
            when (name) {
                "getType" -> Application.ApplicationType.Desktop
                "getGraphics" -> graphics
                else -> null
            }
        }
        ShaderProgram.prependVertexCode = "#version 150\n#define attribute in\n#define varying out\n"
        ShaderProgram.prependFragmentCode = "#version 150\n#define varying in\n#define texture2D texture\n" +
            "#define textureCube texture\n#define gl_FragColor fragColor\nout vec4 fragColor;\n"
    }

    private fun <T> stub(type: Class<T>, answer: (String) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, m, _ ->
            answer(m.name) ?: when (m.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> 0
                java.lang.Float.TYPE -> 0f
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        })
}
