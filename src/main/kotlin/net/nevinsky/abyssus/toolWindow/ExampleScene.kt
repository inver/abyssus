package net.nevinsky.abyssus.toolWindow

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*

/** The lwjgl3-awt demo scene: a rotating quad with per-vertex colors. Must be used with a current GL context. */
class ExampleScene {

    fun init() {
        GL.createCapabilities()
        glClearColor(0.1f, 0.1f, 0.15f, 1f)
    }

    fun render(width: Int, height: Int, angle: Float) {
        glViewport(0, 0, width, height)
        glClear(GL_COLOR_BUFFER_BIT)

        val aspect = width.toFloat() / height.coerceAtLeast(1)
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(-aspect.toDouble(), aspect.toDouble(), -1.0, 1.0, -1.0, 1.0)

        glMatrixMode(GL_MODELVIEW)
        glLoadIdentity()
        glRotatef(angle, 0f, 0f, 1f)
        glScalef(0.6f, 0.6f, 1f)

        glBegin(GL_QUADS)
        glColor3f(1f, 0f, 0f); glVertex2f(-1f, -1f)
        glColor3f(0f, 1f, 0f); glVertex2f(1f, -1f)
        glColor3f(0f, 0f, 1f); glVertex2f(1f, 1f)
        glColor3f(1f, 1f, 0f); glVertex2f(-1f, 1f)
        glEnd()
    }
}
