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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.DocumentReferenceProvider
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
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import net.nevinsky.abyssus.projectView.editSceneJson
import net.nevinsky.abyssus.projectView.selectEntityInAbyssusView
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class SceneFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile) = ProjectLayout.isScene(file)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = SceneFileEditor(project, file)

    override fun getEditorTypeId() = EDITOR_TYPE_ID

    override fun getPolicy() = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "abyssus-scene-view"
    }
}

/**
 * Live view of a `.scene`; re-reads it through [paramsSource] as its sources are edited or change on disk. Moving or
 * rotating an object in the view writes the scene as one undoable edit, and Undo in this tab reaches it.
 * The view comes from [viewFactory], so tests can replace the GL panel.
 */
class SceneFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val paramsSource: SceneParamsSource = SceneParamsSource.EDITOR_TEXT,
    private val viewFactory: (SceneRenderParams) -> SceneView = { SceneViewPanel(it) },
) : UserDataHolderBase(), FileEditor, DocumentReferenceProvider {
    private val content = JPanel(BorderLayout()).apply { isFocusable = true }
    private var view: SceneView? = null

    private var disposed = false

    /** Non-null while the tab shows a message instead of a render. */
    internal var statusText: String? = null
        private set

    init {
        reload()
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { it is VFileContentChangeEvent && isSource(it.file) }) {
                    ApplicationManager.getApplication().invokeLater { if (!disposed) reload() }
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

    private fun isSource(changed: VirtualFile) = changed in paramsSource.sources(file)

    private fun reload() {
        runCatchingKeepingCancellation { paramsSource.read(file) }
            .onSuccess { showScene(it) }
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
        created.onPick = { entityId -> selectEntityInAbyssusView(project, file, entityId) }
        created.onTransform = ::applyTransform
        view = created
        statusText = null
        setContent(created.view)
    }

    /** Writes [edit] to the entity [entityId] of the scene as one undoable command; false when nothing changed. */
    internal fun applyTransform(entityId: String, edit: TransformEdit): Boolean {
        val command = AbyssusBundle.message(if (edit.rotation != null) "commandRotateEntity" else "commandMoveEntity")
        return editSceneJson(project, file, command) { root -> SceneTransformWriter.apply(root, entityId, edit) }
    }

    /** The scene's document, so that Undo and Redo in this tab reach the edits made here. */
    override fun getDocumentReferences(): Collection<DocumentReference> {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()
        return listOf(DocumentReferenceManager.getInstance().create(document))
    }

    internal fun showGlFailure(e: Throwable) {
        if (disposed) return
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
        disposed = true
        view?.let { Disposer.dispose(it) }
        view = null
    }
}
