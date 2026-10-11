/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.DocumentReferenceProvider
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.ui.model.UiIssue
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.textOf
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.Executor
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/** The `.abss` of the native project that owns the asset `meta.json` [file], or null (a loose file, or not an asset's meta). */
fun uiAssetOwner(file: VirtualFile): VirtualFile? {
    if (file.isDirectory || file.name != META_FILE) return null
    val assets = file.parent?.parent?.takeIf { it.name == ASSETS_DIR } ?: return null
    return assets.parent?.children?.firstOrNull { it.extension == ProjectLayout.PROJECT_EXTENSION }
}

private const val MAX_SNIFF_BYTES = 8L * 1024 * 1024

/** Whether [file] is a `meta.json` of type `UI` inside a project; the type is all that is read, so an unsupported UI version is still ours. */
fun isUiAsset(file: VirtualFile): Boolean {
    if (uiAssetOwner(file) == null || file.length > MAX_SNIFF_BYTES) return false
    return runCatchingKeepingCancellation { SceneJson().parse(textOf(file))["type"]?.takeIf { it.isTextual }?.textValue() == "UI" }.getOrDefault(false)
}

/** The visual editor of a `UI` asset, beside the text tab. Refused or unreadable documents show their reason and are never rewritten. */
class UiFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile) = isUiAsset(file)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = UiFileEditor(project, file)

    override fun getEditorTypeId() = EDITOR_TYPE_ID

    override fun getPolicy() = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "abyssus-ui-editor"
    }
}

/**
 * One UI editor tab. Re-reads the document as it is edited (typing waits for a pause, Undo and Redo do not), builds
 * [panelFactory]'s view for an editable asset and a message for one that is not. The panel is a factory so tests can leave
 * out the GL canvas.
 */
class UiFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    background: Executor = Executor { AppExecutorUtil.getAppExecutorService().execute(it) },
    private val panelFactory: (UiEditorController) -> JComponent = { defaultPanel(project, file, it) },
) : UserDataHolderBase(), FileEditor, DocumentReferenceProvider {
    private val content = JPanel(BorderLayout())
    internal val controller = UiEditorController(project, file, background, { ApplicationManager.getApplication().invokeLater(it, ModalityState.any()) },
        preferences = PropertiesUiPreferences(project))
    private val reloads = MergingUpdateQueue("abyssus-ui-reload", RELOAD_PAUSE_MS, true, content, this, null, Alarm.ThreadToUse.SWING_THREAD)
    private var panel: JComponent? = null
    private var disposed = false
    private val banner = JBLabel().apply { isVisible = false; foreground = com.intellij.ui.JBColor.RED; border = javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8) }

    /** The message the tab shows instead of an editor, or null for an editable asset. For tests. */
    internal var statusText: String? = null
        private set

    init {
        controller.addListener(::refreshContent)
        controller.reload()
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val changed = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(event.document)
                if (changed != file) return
                val undoing = UndoManager.getInstance(project).let { it.isUndoInProgress || it.isRedoInProgress }
                if (undoing) { reloads.cancelAllUpdates(); controller.reload() }
                else reloads.queue(object : Update("reload") { override fun run() { if (!disposed) controller.reload() } })
            }
        }, this)
    }

    init {
        // an image or font the asset refers to changed on disk: the canvas reloads its resources
        val folder = file.parent
        project.messageBus.connect(this).subscribe(com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES, object : com.intellij.openapi.vfs.newvfs.BulkFileListener {
            override fun after(events: List<com.intellij.openapi.vfs.newvfs.events.VFileEvent>) {
                val base = folder?.path ?: return
                if (events.any { it.path.startsWith("$base/") && !it.path.endsWith("/$META_FILE") }) {
                    ApplicationManager.getApplication().invokeLater({ if (!disposed) controller.resourcesChanged() }, ModalityState.any())
                }
            }
        })
    }

    /** Runs the re-read waiting for the typing pause, if any. For tests. */
    internal fun flushPendingReload() = reloads.flush()

    private fun refreshContent() {
        if (disposed) return
        val state = controller.state
        if (state is UiEditorState.Editable) {
            statusText = null
            banner.isVisible = false
            // the panel (and the GL canvas in it) is made once; a later invalid moment never rebuilds it
            if (panel == null) setContent(panelFactory(controller).also { panel = it })
            return
        }
        val text = when (state) {
            is UiEditorState.Refused -> AbyssusBundle.message("uiEditorRefused") + " " + describe(state.issues)
            is UiEditorState.Unreadable -> AbyssusBundle.message("uiEditorUnreadable", state.message)
            else -> ""
        }
        statusText = text
        val html = "<html>" + text.replace("\n", "<br>") + "</html>"
        if (panel != null) {
            // interaction is suspended (the canvas draws nothing) until the text is valid again
            banner.text = html
            banner.isVisible = true
        } else {
            setContent(JBScrollPane(JBLabel(html, SwingConstants.CENTER)))
        }
    }

    private fun describe(issues: List<UiIssue>): String = issues.take(8).joinToString("\n") { uiIssueMessage(it) }

    private fun setContent(component: JComponent) {
        content.removeAll()
        content.add(banner, BorderLayout.NORTH)
        content.add(component, BorderLayout.CENTER)
        content.revalidate()
        content.repaint()
    }

    override fun getDocumentReferences(): Collection<DocumentReference> {
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()
        return listOf(DocumentReferenceManager.getInstance().create(document))
    }

    override fun getComponent(): JComponent = content
    override fun getPreferredFocusedComponent(): JComponent = panel ?: content
    override fun getName() = AbyssusBundle.message("uiEditorName")
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) {}
    override fun isModified() = false
    override fun isValid() = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        disposed = true
        reloads.cancelAllUpdates()
        controller.dispose()
    }

    private companion object {
        const val RELOAD_PAUSE_MS = 200
    }
}

/** The real editor view: the panels around the GL canvas, or a message when OpenGL cannot be used. */
internal fun defaultPanel(project: Project, file: VirtualFile, controller: UiEditorController): JComponent {
    val core = com.intellij.openapi.components.service<net.nevinsky.abyssus.plugin.AbyssusCore>()
    val abss = uiAssetOwner(file)
    val canvas = try {
        UiCanvasView(controller, file.parent.name, java.io.File(file.parent.path), core.documents.json, ProjectUiSource(abss)) { sceneName ->
            val scene = abss?.parent?.findChild("scenes")?.findChild(sceneName) ?: error("scene $sceneName not found")
            SceneBackdrop(project, scene)
        }
    } catch (e: Throwable) {
        com.intellij.openapi.diagnostic.Logger.getInstance(UiFileEditor::class.java).warn("Failed to create the UI canvas", e)
        return JBLabel(AbyssusBundle.message("uiCanvasUnavailable", e.message.orEmpty()), SwingConstants.CENTER)
    }
    val scenes = { abss?.parent?.findChild("scenes")?.children?.filter { it.extension == "scene" }?.map { it.name }?.sorted().orEmpty() }
    return UiEditorPanel(project, controller, canvas, scenes).also { com.intellij.openapi.util.Disposer.register(controller, canvas) }
}

/** The message for one issue: its code's text with its location. */
fun uiIssueMessage(issue: UiIssue): String = AbyssusBundle.message("uiIssue.${issue.code.name}", issue.path)
