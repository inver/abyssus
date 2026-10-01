package net.nevinsky.abyssus.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.Timer

/** Swing panel hosting an OpenGL canvas that renders [ExampleScene]. */
class GlScenePanel : JPanel(BorderLayout()), Disposable {

    private val scene = ExampleScene()
    private var angle = 0f

    private val canvas = object : AWTGLCanvas(GLData()) {
        override fun initGL() = scene.init()

        override fun paintGL() {
            scene.render(framebufferWidth, framebufferHeight, angle)
            swapBuffers()
        }
    }

    private val timer: Timer = Timer(16) {
        angle = (angle + 1f) % 360f
        try {
            canvas.render()
        } catch (e: Throwable) {
            thisLogger().warn("GL render failed, stopping animation", e)
            stopAnimation()
        }
    }

    init {
        add(canvas, BorderLayout.CENTER)
    }

    override fun addNotify() {
        super.addNotify()
        timer.start()
    }

    override fun removeNotify() {
        stopAnimation()
        super.removeNotify()
    }

    private fun stopAnimation() {
        timer.stop()
    }

    override fun dispose() {
        stopAnimation()
        canvas.disposeCanvas()
    }
}
