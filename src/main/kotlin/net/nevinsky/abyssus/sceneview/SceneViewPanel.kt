package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.GdxGlBridge
import com.intellij.openapi.diagnostic.thisLogger
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Frame
import java.awt.Graphics
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Only touch GL while the surface is on screen, non-empty and its window is not minimized. On macOS a zero-sized
 * surface makes Metal reject the backing texture and abort the whole JVM (e.g. when switching to the Text tab).
 */
fun canRender(showing: Boolean, width: Int, height: Int, minimized: Boolean) =
    showing && width > 0 && height > 0 && !minimized

fun canRender(component: Component): Boolean {
    val frame = SwingUtilities.getWindowAncestor(component) as? Frame
    val minimized = frame != null && (frame.extendedState and Frame.ICONIFIED) != 0
    return canRender(component.isShowing, component.width, component.height, minimized)
}

/** Swing panel hosting a core-profile GL canvas that renders a scene with libGDX. Read-only; orbit/pan/zoom with the mouse. */
class SceneViewPanel(initial: SceneRenderParams) : JPanel(BorderLayout()), SceneView {

    private val frame = GdxFrame()
    private val renderer = SceneRenderer().also { it.params = initial }
    private val orbit = OrbitCamera.from(initial.camera)
    private var gdx: GdxContext? = null

    private var lastCamera = initial.camera
    private var capabilities: GLCapabilities? = null

    override val view: JComponent get() = this

    /** Called (on the AWT thread) when rendering fails, e.g. when no GL 3.2 core context can be created. */
    override var onFailure: ((Throwable) -> Unit)? = null

    private val canvas = object : AWTGLCanvas(glData()) {
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

        // AWT repaints the canvas itself (layout, tab switches); those paints must obey the same rule as the timer
        override fun paint(g: Graphics) {
            if (canRender(this)) super.paint(g)
        }

        override fun update(g: Graphics) {
            if (canRender(this)) super.update(g)
        }

        /**
         * Disposing the GL context makes it current, which aborts the JVM on a hidden/empty surface (see [canRender]).
         * In that case the context is abandoned (leaked) instead.
         */
        override fun removeNotify() {
            if (context != 0L && !canRender(this)) {
                thisLogger().warn("Scene view removed while hidden; abandoning its GL context instead of disposing it")
                gdx = null
                context = 0L
                initCalled = false
            }
            super.removeNotify()
        }

        override fun paintGL() {
            val ctx = gdx ?: return
            capabilities?.let { GL.setCapabilities(it) }
            frame.tick(framebufferWidth, framebufferHeight)
            GdxRuntime.withContext(ctx) { renderer.render(frame.width, frame.height, orbit) }
            swapBuffers()
        }
    }

    private val timer: Timer = Timer(FRAME_MILLIS) {
        if (!canRender(canvas)) return@Timer
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
        val input = object : MouseAdapter() {
            private var last: Point? = null

            override fun mousePressed(e: MouseEvent) {
                last = e.point
            }

            override fun mouseDragged(e: MouseEvent) {
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
        canvas.addMouseListener(input)
        canvas.addMouseMotionListener(input)
        canvas.addMouseWheelListener(input)
    }

    override fun setParams(params: SceneRenderParams) {
        renderer.params = params
        if (params.camera != lastCamera) {
            lastCamera = params.camera
            orbit.reset(params.camera)
        }
    }

    override fun addNotify() {
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
