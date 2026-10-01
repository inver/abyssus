package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
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

/** Read-only live view of a `.scene`; re-reads the scene (and its project's camera) as the files are edited or change on disk. */
class SceneFileEditor(
    project: Project,
    private val file: VirtualFile,
    private val viewFactory: (SceneRenderParams) -> SceneView = { SceneViewPanel(it) },
) : UserDataHolderBase(), FileEditor {
    private val content = JPanel(BorderLayout()).apply { isFocusable = true }
    private var view: SceneView? = null

    /** Non-null while the tab shows a message instead of a render. */
    internal var statusText: String? = null
        private set

    init {
        reload()
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { it is VFileContentChangeEvent && isSource(it.file) }) {
                    ApplicationManager.getApplication().invokeLater { if (!Disposer.isDisposed(this@SceneFileEditor)) reload() }
                }
            }
        })
        // unsaved edits in the text tabs of the scene or of its project file
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val changed = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (isSource(changed)) reload()
            }
        }, this)
    }

    private fun isSource(changed: VirtualFile) = changed == file || changed == MainCamera.abssFor(file)

    private fun reload() {
        val params = runCatching { SceneReader.parse(textOf(file)) }.map { SceneRenderParams.from(it, MainCamera.forScene(file)) }
        params.onSuccess { showScene(it) }
            .onFailure { showStatus(AbyssusBundle.message("sceneViewParseError", it.message ?: it.javaClass.simpleName)) }
    }

    private fun showScene(params: SceneRenderParams) {
        view?.let {
            it.setParams(params)
            return
        }
        val created = try {
            viewFactory(params)
        } catch (e: Throwable) {
            thisLogger().warn("Failed to create OpenGL scene view", e)
            showStatus(AbyssusBundle.message("glUnavailable", e.message ?: e.javaClass.simpleName))
            return
        }
        created.onFailure = { e -> ApplicationManager.getApplication().invokeLater { showGlFailure(e) } }
        view = created
        statusText = null
        setContent(created.view)
    }

    internal fun showGlFailure(e: Throwable) {
        if (Disposer.isDisposed(this)) return
        showStatus(AbyssusBundle.message("glUnavailable", e.message ?: e.javaClass.simpleName))
    }

    private fun showStatus(text: String) {
        view?.let { Disposer.dispose(it) }
        view = null
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
    override fun getPreferredFocusedComponent(): JComponent = content
    override fun getName() = AbyssusBundle.message("sceneViewEditorName")
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) {}
    override fun isModified() = false
    override fun isValid() = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        view?.let { Disposer.dispose(it) }
        view = null
    }
}
