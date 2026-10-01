package net.nevinsky.abyssus.toolWindow

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import net.nevinsky.abyssus.AbyssusBundle
import javax.swing.JComponent


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    private val contentFactory = ContentFactory.SERVICE.getInstance()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = contentFactory.createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val toolWindow: ToolWindow) {

        fun getContent(): JComponent = try {
            GlScenePanel().also { Disposer.register(toolWindow.disposable, it) }
        } catch (e: Throwable) {
            thisLogger().warn("Failed to create OpenGL panel", e)
            JBLabel(AbyssusBundle.message("glUnavailable", e.message ?: e.javaClass.simpleName))
        }
    }
}
