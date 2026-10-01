package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.components.JBLabel
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.SceneReader
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class SceneFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile) = !file.isDirectory && file.extension == "scene"

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = SceneFileEditor(project, file)

    override fun getEditorTypeId() = EDITOR_TYPE_ID

    override fun getPolicy() = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "abyssus-scene-view"
    }
}

/** Read-only live view of a `.scene`; re-reads the scene when the file changes. */
class SceneFileEditor(project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val content = JPanel(BorderLayout())
    private var panel: SceneViewPanel? = null

    /** Non-null while the tab shows a message instead of a render. */
    internal var statusText: String? = null
        private set

    init {
        reload()
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { it is VFileContentChangeEvent && it.file == file }) {
                    ApplicationManager.getApplication().invokeLater { if (!Disposer.isDisposed(this@SceneFileEditor)) reload() }
                }
            }
        })
    }

    private fun reload() {
        val params = SceneReader.readScene(file).map { SceneRenderParams.from(it, MainCamera.forScene(file)) }
        params.onSuccess { showScene(it) }
            .onFailure { showStatus(AbyssusBundle.message("sceneViewParseError", it.message ?: it.javaClass.simpleName)) }
    }

    private fun showScene(params: SceneRenderParams) {
        panel?.let {
            it.setParams(params)
            return
        }
        val created = try {
            SceneViewPanel(params)
        } catch (e: Throwable) {
            thisLogger().warn("Failed to create OpenGL scene view", e)
            showStatus(AbyssusBundle.message("glUnavailable", e.message ?: e.javaClass.simpleName))
            return
        }
        created.onFailure = { e -> ApplicationManager.getApplication().invokeLater { showGlFailure(e) } }
        panel = created
        statusText = null
        setContent(created)
    }

    internal fun showGlFailure(e: Throwable) {
        if (Disposer.isDisposed(this)) return
        showStatus(AbyssusBundle.message("glUnavailable", e.message ?: e.javaClass.simpleName))
    }

    private fun showStatus(text: String) {
        panel?.let { Disposer.dispose(it) }
        panel = null
        statusText = text
        setContent(JBLabel(text, SwingConstants.CENTER))
    }

    private fun setContent(component: JComponent) {
        content.removeAll()
        content.add(component, BorderLayout.CENTER)
        content.revalidate()
        content.repaint()
    }

    override fun getComponent(): JComponent = content
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun getName() = AbyssusBundle.message("sceneViewEditorName")
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) {}
    override fun isModified() = false
    override fun isValid() = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        panel?.let { Disposer.dispose(it) }
        panel = null
    }
}
