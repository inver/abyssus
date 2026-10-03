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
 * Runs a block once with a GL 3.2 core context current and libGDX's `Gdx.gl*` installed. Opens a small window, so
 * tests using it only run with `-Dabyssus.glTests=true` (and a display). In libGDX's package because `Lwjgl3GL30` is
 * package-private.
 */
object TestGl {
    val enabled: Boolean get() = System.getProperty("abyssus.glTests") == "true" && !GraphicsEnvironment.isHeadless()

    fun <T> run(block: () -> T): T {
        GdxNativesLoader.load()
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        lateinit var window: JFrame
        SwingUtilities.invokeAndWait {
            val data = GLData().apply {
                majorVersion = 3; minorVersion = 2; profile = GLData.Profile.CORE; forwardCompatible = true; depthSize = 24
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
                add(canvas)
                setSize(320, 240)
                isVisible = true
            }
            // the canvas has a context only once it is shown: render from a later event
            javax.swing.Timer(50) { e -> if (result == null) canvas.render() else (e.source as javax.swing.Timer).stop() }.start()
        }
        check(done.await(30, TimeUnit.SECONDS)) { "GL test timed out" }
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
