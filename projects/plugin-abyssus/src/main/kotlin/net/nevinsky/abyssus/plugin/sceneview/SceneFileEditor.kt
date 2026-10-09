/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.pick.SceneTransformWriter
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit
import net.nevinsky.abyssus.plugin.SceneRayControls

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
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
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.components.JBLabel
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.plugin.filetype.editSceneJson
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import net.nevinsky.abyssus.plugin.filetype.AbyssusSceneEdited
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage

/** How long typing must pause before the view re-reads the scene; the view then catches up within 300 ms of the last keystroke. */
private const val RELOAD_PAUSE_MS = 200

/**
 * Live view of a `.scene`; re-reads it through [paramsSource] as its sources are edited or change on disk. Moving or
 * rotating an object in the view writes the scene as one undoable edit, and Undo in this tab reaches it.
 * The view comes from [viewFactory], so tests can replace the GL panel; [json] reads asset metadata and [rayControls]
 * registers the view's Ray Tracing switch.
 */
class SceneFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val json: JsonProcessor,
    private val rayControls: SceneRayControls,
    private val paramsSource: SceneParamsSource,
    private val host: SceneViewHost,
    private val viewFactory: (SceneRenderParams) -> SceneView,
) : UserDataHolderBase(), FileEditor, DocumentReferenceProvider {
    private val content = JPanel(BorderLayout()).apply { isFocusable = true }
    private var view: SceneView? = null
    private val reloads = ReloadPolicy()
    private val reloadQueue = MergingUpdateQueue("abyssus-scene-reload", RELOAD_PAUSE_MS, true, content, this, null, Alarm.ThreadToUse.SWING_THREAD)

    private var disposed = false

    /** Reloads assets that change on disk or in an editor; null for a scene outside a project. */
    private val assetRefresh: AssetRefresh? = ProjectLayout.projectDirFor(file)?.let { dir ->
        AssetRefresh(
            dir, json,
            unsavedMeta = { unsavedAssetMeta(dir) },
            background = { AppExecutorUtil.getAppExecutorService().execute(it) },
            ui = { ApplicationManager.getApplication().invokeLater({ if (!disposed) it.run() }, ModalityState.any()) },
            deliver = { revision -> view?.refreshAssets(revision) },
        )
    }

    /** Non-null while the tab shows a message instead of a render. */
    internal var statusText: String? = null
        private set

    // Physics availability is handled separately; it is not a render input.
    private var projectInputs: JsonNode? = projectInputs()

    private fun projectInputs(text: String? = null): JsonNode? = runCatchingKeepingCancellation {
        val abss = ProjectLayout.abssFor(file) ?: return@runCatchingKeepingCancellation null
        val root = SceneJson().parse(text ?: textOf(abss))
        AbyssusDocumentFormat().requireSupported(root, DocumentKind.PROJECT)
        (root as ObjectNode).also { it.remove("physicsEnabled") }
    }.getOrNull()

    private fun hasRenderChange(changed: VirtualFile, text: String? = null): Boolean =
        changed == file || projectInputs(text).let { it == null || it != projectInputs }

    init {
        reload()
        assetRefresh?.let { refresh ->
            val assets = AssetsFolder(ProjectLayout.projectDirFor(file)!!)
            refresh.start()
            project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { assets.holds(it.path) }) refresh.changed()
                }
            })
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val changed = FileDocumentManager.getInstance().getFile(event.document) ?: return
                    if (changed.name == META_FILE && assets.contains(changed.path)) refresh.changed()
                }
            }, this)
        }
        host.listen(file, this) { entityId ->
            rayControls.selected(file, this, entityId)
            view?.selectEntity(entityId)
        }
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { it is VFileContentChangeEvent && isSource(it.file) && hasRenderChange(it.file) }) {
                    ApplicationManager.getApplication().invokeLater { if (!disposed) reloadNow() }
                }
            }
        })
        // a plugin edit (gizmo, panel, tree, Add Light, ...) is shown at once, not after the typing pause
        project.messageBus.connect(this).subscribe(AbyssusSceneEdited.TOPIC, AbyssusSceneEdited { edited ->
            if (isSource(edited) && hasRenderChange(edited)) reloadNow()
        })
        // unsaved edits in the text tabs of the scene or of its project file: typing waits for a pause, Undo and Redo do not
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            // any edit, from a text tab, the panel, the tree or a gizmo, stops play before it applies
            override fun beforeDocumentChange(event: DocumentEvent) {
                val changed = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (!isSource(changed)) return
                val next = event.document.text.replaceRange(event.offset, event.offset + event.oldLength, event.newFragment.toString())
                if (hasRenderChange(changed, next)) view?.stopPlay()
            }

            override fun documentChanged(event: DocumentEvent) {
                val changed = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (!isSource(changed) || !hasRenderChange(changed)) return
                val undoing = UndoManager.getInstance(project).let { it.isUndoInProgress || it.isRedoInProgress }
                if (undoing) reloadNow() else reloadAfterPause()
            }
        }, this)
    }

    /** Typing in a text tab: one re-read of the final text once the typing pauses. */
    private fun reloadAfterPause() {
        if (reloads.typing() != Reload.LATER) return
        reloadQueue.queue(object : Update("reload") {
            override fun run() {
                if (reloads.due() && !disposed) reload()
            }
        })
    }

    /** A change that must show at once; it also covers any re-read still waiting for the pause. */
    private fun reloadNow() {
        if (reloads.immediate() != Reload.NOW) return
        reloadQueue.cancelAllUpdates()
        reload()
    }

    /** The scene file this tab shows. */
    internal val sceneFile: VirtualFile get() = file

    /** The live view, or null while the tab shows a message instead of a render. */
    internal val sceneView: SceneView? get() = view

    /** Runs the re-read that is waiting for the typing pause, if any, without waiting; for tests. */
    internal fun flushPendingReload() = reloadQueue.flush()

    private fun isSource(changed: VirtualFile) = changed in paramsSource.sources(file)

    private fun reload() {
        runCatchingKeepingCancellation { paramsSource.read(file) }
            .onSuccess { showScene(it) }
            .onFailure { showStatus(AbyssusBundle.message("sceneViewParseError", it.documentDisplayMessage())) }
    }

    private fun showScene(params: SceneRenderParams) {
        projectInputs = projectInputs()
        rayControls.recordFacts(file, this, params.content)
        view?.let {
            it.setParams(params)
            return
        }
        val created = try {
            viewFactory(params)
        } catch (e: Throwable) {
            thisLogger().warn("Failed to create OpenGL scene view", e)
            showStatus(AbyssusBundle.message("glUnavailable", e.documentDisplayMessage()))
            return
        }
        created.onFailure = { e -> ApplicationManager.getApplication().invokeLater { showGlFailure(e) } }
        created.onPick = { entityId ->
            rayControls.selected(file, this, entityId)
            host.select(file, entityId)
        }
        created.onTransform = ::applyTransform
        (created as? RayControlProvider)?.rayControl?.let { rayControls.register(file, it, created) }
        view = created
        statusText = null
        setContent(created.view)
    }

    /** Writes [edit] to the entity [entityId] of the scene as one undoable command; false when nothing changed. */
    internal fun applyTransform(entityId: String, edit: TransformEdit): Boolean {
        val isRotate = edit.rotation != null || edit.target != null
        val command = AbyssusBundle.message(if (isRotate) "commandRotateEntity" else "commandMoveEntity")
        return editSceneJson(project, file, command) { root -> SceneTransformWriter().apply(root, entityId, edit) }
    }

    /** The scene's document, so that Undo and Redo in this tab reach the edits made here. */
    override fun getDocumentReferences(): Collection<DocumentReference> {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()
        return listOf(DocumentReferenceManager.getInstance().create(document))
    }

    internal fun showGlFailure(e: Throwable) {
        if (disposed) return
        showStatus(AbyssusBundle.message("glUnavailable", e.documentDisplayMessage()))
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
        rayControls.forgetFacts(file, this)
        reloads.dispose()
        reloadQueue.cancelAllUpdates()
        assetRefresh?.dispose()
        view?.let { Disposer.dispose(it) }
        view = null
    }
}

/**
 * The text of every asset `meta.json` of the project in [projectDir] that has unsaved changes in an editor, by file. Read
 * on the UI thread, so the background read of the assets never touches documents.
 */
internal fun unsavedAssetMeta(projectDir: File): Map<File, String> {
    val assets = AssetsFolder(projectDir)
    val manager = FileDocumentManager.getInstance()
    return manager.unsavedDocuments.mapNotNull { document ->
        val file = manager.getFile(document)?.takeIf { it.name == META_FILE } ?: return@mapNotNull null
        val io = File(file.path).absoluteFile
        if (assets.contains(io.path)) io to document.text else null
    }.toMap()
}
