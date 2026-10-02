/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.GdxGlBridge
import com.intellij.openapi.diagnostic.thisLogger
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.awt.GLData
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/** Swing panel hosting a core-profile GL canvas that renders a scene with libGDX. Read-only; orbit/pan/zoom with the mouse. */
class SceneViewPanel(
    initial: SceneRenderParams,
    private val renderer: SceneRenderer = SceneRenderer(),
) : JPanel(BorderLayout()), SceneView {

    private val frame = GdxFrame()
    private val orbit = OrbitCamera.from(initial.camera)
    private var gdx: GdxContext? = null

    private var lastCamera = initial.camera
    private var capabilities: GLCapabilities? = null

    override val view: JComponent get() = this

    init {
        renderer.params = initial
    }

    /** Called (on the AWT thread) when rendering fails, e.g. when no GL 3.2 core context can be created. */
    override var onFailure: ((Throwable) -> Unit)? = null

    /** Set when the canvas had to give up its context while hidden; its native surface can no longer be trusted. */
    private var abandoned = false

    private fun newCanvas(): GuardedGLCanvas = object : GuardedGLCanvas(glData()) {
        override fun initGL() {
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
                GdxRuntime.withContext(ctx) { renderer.dispose() }
            } catch (e: Throwable) {
                thisLogger().warn("Failed to release scene view GL resources", e)
            }
        }

        override fun onContextAbandoned() {
            gdx = null
            abandoned = true
        }

        override fun paintGL() {
            val ctx = gdx ?: return
            capabilities?.let { GL.setCapabilities(it) }
            frame.tick(framebufferWidth, framebufferHeight)
            GdxRuntime.withContext(ctx) { renderer.render(frame.width, frame.height, orbit, frame.deltaSeconds) }
            swapBuffers()
        }
    }

    private var canvas: GuardedGLCanvas = newCanvas()

    private val timer: Timer = Timer(FRAME_MILLIS) {
        if (!canvas.glSafe()) return@Timer
        try {
            canvas.render()
        } catch (e: Throwable) {
            thisLogger().warn("Scene render failed, stopping the view", e)
            stopLoop()
            onFailure?.invoke(e)
        }
    }

    private fun stopLoop() = timer.stop()

    init {
        add(canvas, BorderLayout.CENTER)
        attachInput(canvas)
    }

    private fun attachInput(target: GuardedGLCanvas) {
        val input = object : MouseAdapter() {
            private var last: Point? = null
            private val click = ClickGesture()

            override fun mousePressed(e: MouseEvent) {
                last = e.point
                click.pressed(e.x, e.y)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (click.released(e.x, e.y) && SwingUtilities.isLeftMouseButton(e)) pick(e.x, e.y)
            }

            override fun mouseDragged(e: MouseEvent) {
                click.dragged(e.x, e.y)
                val from = last ?: return
                val dx = (e.x - from.x).toFloat()
                val dy = (e.y - from.y).toFloat()
                if (SwingUtilities.isLeftMouseButton(e)) orbit.orbit(dx, dy) else orbit.pan(dx, dy)
                last = e.point
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                orbit.zoom(e.preciseWheelRotation.toFloat())
            }
        }
        target.addMouseListener(input)
        target.addMouseMotionListener(input)
        target.addMouseWheelListener(input)
    }

    /**
     * An abandoned canvas keeps a native surface that macOS no longer sizes with the component, so showing it again
     * renders into a stale, cropped drawable. A new canvas gets a new native surface and a new context.
     */
    private fun replaceAbandonedCanvas() {
        if (!abandoned) return
        abandoned = false
        remove(canvas)
        canvas = newCanvas()
        attachInput(canvas)
        add(canvas, BorderLayout.CENTER)
    }

    override var onPick: ((String) -> Unit)? = null

    /** The pixel is in Swing coordinates; the framebuffer may be larger (HiDPI). */
    private fun pick(x: Int, y: Int) {
        if (canvas.width <= 0 || canvas.height <= 0) return
        val sx = x * canvas.framebufferWidth / canvas.width
        val sy = y * canvas.framebufferHeight / canvas.height
        renderer.pick(sx, sy, canvas.framebufferWidth, canvas.framebufferHeight)?.let { onPick?.invoke(it) }
    }

    override fun setParams(params: SceneRenderParams) {
        renderer.params = params
        if (params.camera != lastCamera) {
            lastCamera = params.camera
            orbit.reset(params.camera)
        }
    }

    override fun addNotify() {
        replaceAbandonedCanvas()
        super.addNotify()
        timer.start()
    }

    override fun removeNotify() {
        stopLoop()
        super.removeNotify()
    }

    override fun dispose() {
        stopLoop()
        canvas.disposeCanvas() // releases GL resources through disposeGL while the context is still current
    }

    private companion object {
        const val FRAME_MILLIS = 16

        fun glData() = GLData().apply {
            majorVersion = 3
            minorVersion = 2
            profile = GLData.Profile.CORE
            forwardCompatible = true
            depthSize = 24
            swapInterval = 0 // the Swing timer paces frames; a vsync-blocked swap would stall the IDE thread
        }
    }
}
