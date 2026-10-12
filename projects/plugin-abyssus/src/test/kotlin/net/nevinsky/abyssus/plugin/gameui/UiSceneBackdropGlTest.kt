/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.GdxGlBridge
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.graphics.GL20
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.sceneview.GdxContext
import net.nevinsky.abyssus.plugin.sceneview.GdxFrame
import net.nevinsky.abyssus.plugin.sceneview.GdxRuntime
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.BorderLayout
import java.io.File
import java.nio.ByteBuffer
import javax.swing.JFrame

/**
 * The real [SceneBackdrop] (the scene view's renderer fed the scene's editor text) drawing a copy of the Untitled project's
 * Main Scene in a GL 3.2 core window, with no simulation and no scene file changed. Opens a window: runs only with
 * `-Dabyssus.glTests=true`.
 */
class UiSceneBackdropGlTest : BasePlatformTestCase() {
    fun testTheMainSceneOfUntitledIsDrawnAndTheSceneFileIsNotTouched() {
        if (!GlHarness.enabled) return
        val dir = FileUtil.createTempDirectory("abyssus-backdrop", null)
        File("src/test/testData/project/Untitled").copyRecursively(dir, overwrite = true)
        val sceneFile = File(dir, "scenes/Main Scene.scene")
        val before = sceneFile.readText()
        val scene = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sceneFile)!!
        val frame = GdxFrame()
        var ctx: GdxContext? = null
        var backdrop: SceneBackdrop? = null
        var failure: Throwable? = null
        var image: IntArray? = null
        var size = 0 to 0
        val canvas = object : AWTGLCanvas(GLData().apply {
            majorVersion = 3; minorVersion = 2; profile = GLData.Profile.CORE; forwardCompatible = true; depthSize = 24; swapInterval = 0
        }) {
            override fun initGL() {
                GL.createCapabilities()
                ctx = GdxRuntime.newContext(frame, GdxGlBridge.gl20(), GdxGlBridge.gl30(), Lwjgl3Files())
                GdxRuntime.withContext(ctx!!) { backdrop = SceneBackdrop(project, scene) }
            }

            override fun paintGL() {
                frame.tick(framebufferWidth, framebufferHeight)
                GdxRuntime.withContext(ctx!!) {
                    Gdx.gl.glViewport(0, 0, frame.width, frame.height)
                    Gdx.gl.glClearColor(1f, 0f, 1f, 1f) // a colour the scene never uses, to tell drawn pixels from untouched ones
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                    backdrop!!.render(frame.width, frame.height, frame.deltaSeconds)
                }
                val w = framebufferWidth; val h = framebufferHeight
                val buf = ByteBuffer.allocateDirect(w * h * 4)
                GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf)
                image = IntArray(w * h) { i -> (buf[i * 4].toInt() and 255 shl 16) or (buf[i * 4 + 1].toInt() and 255 shl 8) or (buf[i * 4 + 2].toInt() and 255) }
                size = w to h
                swapBuffers()
            }
        }
        val window = JFrame("Abyssus UI backdrop test").apply { layout = BorderLayout(); add(canvas, BorderLayout.CENTER); setSize(640, 400); isVisible = true }
        try {
            // the scene's assets load over several frames; pump the event queue between renders as the IDE would
            repeat(180) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                try { if (canvas.isShowing) canvas.render() } catch (e: Throwable) { failure = failure ?: e }
                Thread.sleep(16)
            }
            failure?.let { throw it }
            val pixels = checkNotNull(image) { "nothing was rendered" }
            val magenta = 0xff00ff
            val drawn = pixels.count { it != magenta }
            assertTrue("the scene covers the buffer (drawn $drawn of ${pixels.size})", drawn > pixels.size * 0.5)
            assertEquals("the scene file was only read", before, sceneFile.readText())
        } finally {
            ctx?.let { c -> backdrop?.let { b -> runCatching { canvas.executeInContext { GdxRuntime.withContext(c) { b.dispose() } } } } }
            window.dispose()
        }
    }
}
