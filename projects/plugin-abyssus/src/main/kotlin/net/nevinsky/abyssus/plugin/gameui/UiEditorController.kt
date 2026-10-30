/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.ui.*
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutResult
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.model.UiIssue
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.util.concurrent.Executor

/** What the editor knows about the document it shows. */
sealed interface UiEditorState {
    /** An admitted UI asset: editable. */
    data class Editable(val admission: UiAdmission.Admitted, val text: String) : UiEditorState {
        val collection: UiCollection get() = admission.collection
    }

    /** Valid JSON that is not editable UI (unsupported version, foreign markers, errors): nothing is rewritten. */
    data class Refused(val issues: List<UiIssue>, val text: String) : UiEditorState

    /** The text is not a JSON document at all (half-typed in the text editor, say). */
    data class Unreadable(val message: String, val text: String) : UiEditorState
}

enum class UiEditorMode { EDIT, PREVIEW }

/** The size of the screen a preview lays out for, independent of how large the editor panel shows it. */
data class UiViewport(val width: Int, val height: Int) {
    val valid: Boolean get() = width in 1..MAX_PREVIEW_SIDE && height in 1..MAX_PREVIEW_SIDE
}

/** The largest preview side; a bigger offscreen buffer costs memory the editor should not spend on a preview. */
const val MAX_PREVIEW_SIDE = 4096

/** Editor-local settings (the preview size and backdrop): per viewer, never part of a scene or UI document. */
interface UiPreferences {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

/** Preferences kept in the project's workspace file, so they are per viewer and never part of the repository's content. */
class PropertiesUiPreferences(private val project: Project) : UiPreferences {
    override fun get(key: String): String? = com.intellij.ide.util.PropertiesComponent.getInstance(project).getValue(key)
    override fun set(key: String, value: String?) {
        val properties = com.intellij.ide.util.PropertiesComponent.getInstance(project)
        if (value == null) properties.unsetValue(key) else properties.setValue(key, value)
    }
}

class MemoryUiPreferences : UiPreferences {
    private val values = HashMap<String, String>()
    override fun get(key: String) = values[key]
    override fun set(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
}

/** What the canvas needs from the editor model: what to draw, which mode, and the pointer gestures it forwards. */
interface UiCanvasModel {
    val state: UiEditorState
    val mode: UiEditorMode
    val screenId: String?
    val selectedWidgetId: String?
    val gestureActive: Boolean

    /** The preview's isolated sample world; null in edit mode and while the document is not editable UI. */
    val previewState: net.nevinsky.abyssus.lib.core.editor.ui.UiPreviewState?
    val previewViewport: UiViewport

    /** The scene (a file name in the project's `scenes` folder) drawn behind a preview, or null for none. */
    val backdropScene: String?

    /** Bumped when a file the UI refers to (an image, a font) changed on disk, so the canvas reloads its resources. */
    val resourceRevision: Long

    /** Set by the canvas: the layout it currently shows for a screen, for drag gestures. */
    var layoutProvider: (String) -> UiLayoutResult?

    fun addListener(listener: () -> Unit)

    /** Told by the canvas how large the displayed screen is drawn, as a fraction of its own size (1 = 100%). */
    fun reportZoom(zoom: Float) {}
    fun select(widgetId: String?)
    fun beginGesture(widgetId: String, x: Float, y: Float)
    fun updateGesture(x: Float, y: Float): UiGesturePreview
    fun endGesture(x: Float, y: Float): Boolean
    fun deleteSelected(): Boolean
    fun duplicateSelected(): Boolean
}

/**
 * The model of one UI editor tab, with no Swing: the parsed document, selection, mode and the edits that panels and the
 * canvas ask for. Documents and selection are captured on the UI thread; parsing and validation run on [background] and
 * their result is applied on the UI thread only if no newer read was requested meanwhile (a stale worker result is
 * dropped). Every write is an `editUiMeta` command. UI thread only, except where [background] is mentioned.
 */
class UiEditorController(
    private val project: Project,
    val file: VirtualFile,
    private val background: Executor,
    private val ui: (Runnable) -> Unit,
    private val decoder: UiCollectionDecoder = UiCollectionDecoder(),
    private val textEditor: UiTextEditor = UiTextEditor(),
    /** The layout the canvas shows for a screen, for drag gestures; null while nothing is drawn. */
    override var layoutProvider: (String) -> UiLayoutResult? = { null },
    private val preferences: UiPreferences = MemoryUiPreferences(),
    private val previews: UiPreviewState.Factory = UiPreviewState.Factory(),
) : Disposable, UiCanvasModel {
    private val json = SceneJson()
    private var revision = 0L
    private var disposed = false

    override var state: UiEditorState = UiEditorState.Unreadable("", "")
        private set
    var selection: UiSelection = UiSelection()
        private set
    override var mode: UiEditorMode = UiEditorMode.EDIT
        private set

    /** The screen whose widgets the canvas and tree show. */
    override var screenId: String? = null
        private set

    private val prefKey: String get() = "abyssus.ui.${file.path}"

    private val viewSettings = UiViewSettings(preferences, prefKey)

    override val previewViewport: UiViewport get() = viewSettings.viewport

    override val backdropScene: String? get() = viewSettings.backdropScene

    override var resourceRevision: Long = 0
        private set

    /** The preview's sample world, created when preview starts for the current definition and dropped with it. */
    override var previewState: UiPreviewState? = null
        private set

    /** The reason the last edit was refused, for the status line; cleared by the next successful edit. */
    var lastError: UiEditError? = null
        private set

    private val listeners = mutableListOf<() -> Unit>()

    override fun addListener(listener: () -> Unit) { listeners += listener }

    private fun changed() = listeners.toList().forEach { it() }

    /** How large the canvas draws the displayed screen (1 = 100%); set by the canvas, shown in the toolbar. */
    var zoom: Float = 1f
        private set

    private val zoomListeners = mutableListOf<() -> Unit>()

    fun addZoomListener(listener: () -> Unit) { zoomListeners += listener }

    override fun reportZoom(zoom: Float) {
        if (zoom <= 0f || kotlin.math.abs(zoom - this.zoom) < 0.005f) return
        this.zoom = zoom
        zoomListeners.toList().forEach { it() }
    }

    val collection: UiCollection? get() = (state as? UiEditorState.Editable)?.collection

    val text: String get() = when (val s = state) {
        is UiEditorState.Editable -> s.text
        is UiEditorState.Refused -> s.text
        is UiEditorState.Unreadable -> s.text
    }

    // ---- reading ----

    /** Reads the document now (unsaved text included) and publishes the parsed state when this is still the newest read. */
    fun reload() {
        if (disposed) return
        val document = FileDocumentManager.getInstance().getDocument(file)
        val snapshot = document?.text ?: String(file.contentsToByteArray(), file.charset)
        val mine = ++revision
        background.execute {
            val parsed = parse(snapshot)
            ui(Runnable {
                if (disposed || mine != revision) return@Runnable // a newer read superseded this result
                apply(parsed)
            })
        }
    }

    private fun parse(text: String): UiEditorState {
        val tree = runCatchingKeepingCancellation { json.parse(text) }.getOrElse { return UiEditorState.Unreadable(it.message.orEmpty(), text) }
        return when (val admission = decoder.decode(tree)) {
            is UiAdmission.Admitted -> UiEditorState.Editable(admission, text)
            is UiAdmission.Refused -> UiEditorState.Refused(admission.issues, text)
        }
    }

    private fun apply(parsed: UiEditorState) {
        val definitionChanged = (state as? UiEditorState.Editable)?.text != (parsed as? UiEditorState.Editable)?.text
        state = parsed
        if (definitionChanged) previewState = null
        val collection = (parsed as? UiEditorState.Editable)?.collection
        if (collection == null) { selection = UiSelection(); screenId = null }
        else {
            val keep = screenId?.takeIf { collection.screen(it) != null }
                ?: collection.entryScreen?.takeIf { collection.screen(it) != null } ?: collection.screens.firstOrNull()?.id
            screenId = keep
            selection = selection.reconcile(collection).let { if (it.screenId == keep || it.widgetId == null) it else UiSelection(keep, collection.screen(keep!!)?.root?.id) }
        }
        syncPreview()
        changed()
    }

    // ---- selection and mode ----

    override fun select(widgetId: String?) {
        val c = collection ?: return
        selection = if (widgetId == null) UiSelection(screenId, null) else UiSelection(UiTree(c).screenOf(widgetId)?.id ?: screenId, widgetId).takeIf { UiTree(c).widget(widgetId) != null } ?: selection
        selection.screenId?.let { if (c.screen(it) != null) screenId = it }
        changed()
    }

    fun selectScreen(id: String) {
        val c = collection ?: return
        val screen = c.screen(id) ?: return
        screenId = id
        selection = UiSelection(id, screen.root.id)
        changed()
    }

    fun setMode(mode: UiEditorMode) {
        if (this.mode == mode) return
        this.mode = mode
        syncPreview()
        changed()
    }

    /** Preview exists only in preview mode on an admitted document; its samples start fresh for each definition. */
    private fun syncPreview() {
        val c = collection
        if (mode != UiEditorMode.PREVIEW || c == null) { if (c == null) previewState = null; return }
        if (previewState == null) previewState = previews.of(c, screenId ?: c.entryScreen)
    }

    /** The preview's sample world changed shape (a row added or removed): tell the views. */
    fun resetView() { changed() }

    /** Returns the samples to the authored ones, empties the action log and sends the preview back to its start screen. */
    fun resetPreview() { previewState?.reset(); changed() }

    /** Sets what the preview lays out for; false (nothing changes) for a size that is not positive or is above [MAX_PREVIEW_SIDE]. */
    fun setPreviewViewport(width: Int, height: Int): Boolean {
        if (!viewSettings.setViewport(width, height)) return false
        changed()
        return true
    }

    /** Chooses the scene drawn behind the preview (a file name in `scenes`), or none; remembered for this viewer only. */
    fun setBackdropScene(name: String?) {
        viewSettings.setBackdrop(name)
        changed()
    }

    /** An image or font file of the asset changed on disk: the canvas reloads its resources. */
    fun resourcesChanged() { resourceRevision++; changed() }

    val selectedWidget: UiWidget? get() = collection?.let { c -> selection.widgetId?.let { UiTree(c).widget(it) } }

    // ---- edits ----

    private fun perform(name: String, baseText: String? = null, op: (UiDocumentEdits, JsonNode) -> UiEditResult): UiEditOutcome {
        val outcome = editUiMeta(project, file, name, baseText, textEditor, op)
        lastError = when (outcome) {
            is UiEditOutcome.Rejected -> outcome.error
            UiEditOutcome.Stale -> UiEditError.STALE
            UiEditOutcome.ReadOnly -> UiEditError.NOT_EDITABLE
            UiEditOutcome.Unavailable -> UiEditError.NOT_EDITABLE
            else -> null
        }
        if (outcome is UiEditOutcome.Edited) { reloadNow() } else changed()
        return outcome
    }

    /** The write already changed the document; read it back at once (on this thread) so the next edit sees it. */
    private fun reloadNow() {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        revision++
        apply(parse(document.text))
    }

    private fun added(outcome: UiEditOutcome): String? = ((outcome as? UiEditOutcome.Edited)?.result as? UiEditResult.Added)?.id

    /** The container [addWidget] adds into: the selected container, else the selected widget's parent, else the screen's root; null when there is none. */
    fun addTarget(): UiWidget? {
        val c = collection ?: return null
        val tree = UiTree(c)
        val selected = selection.widgetId?.let(tree::widget) ?: c.screen(screenId ?: return null)?.root ?: return null
        return if (tree.isContainer(selected)) selected else tree.parentOf(selected.id)
    }

    /** Adds a widget of [type] into the selected container (or the selected widget's parent), and selects it. */
    fun addWidget(type: String): Boolean {
        val parent = addTarget() ?: return false
        val id = added(perform(AbyssusBundle.message("uiCommandAddWidget")) { e, m -> e.addWidget(m, parent.id, type) }) ?: return false
        select(id)
        return true
    }

    override fun duplicateSelected(): Boolean {
        val id = selection.widgetId ?: return false
        val copy = added(perform(AbyssusBundle.message("uiCommandDuplicateWidget")) { e, m -> e.duplicateWidget(m, id) }) ?: return false
        select(copy)
        return true
    }

    override fun deleteSelected(): Boolean {
        val c = collection ?: return false
        val id = selection.widgetId ?: return false
        val parent = UiTree(c).parentOf(id)?.id
        val ok = perform(AbyssusBundle.message("uiCommandDeleteWidget")) { e, m -> e.deleteWidget(m, id) } is UiEditOutcome.Edited
        if (ok) select(parent)
        return ok
    }

    /** Tree drag and drop: moves the selected widget under [parentId] at [index]. */
    fun moveSelected(parentId: String, index: Int?): Boolean {
        val id = selection.widgetId ?: return false
        return perform(AbyssusBundle.message("uiCommandMoveWidget")) { e, m -> e.reparentWidget(m, id, parentId, index) } is UiEditOutcome.Edited
    }

    fun reorderSelected(newIndex: Int): Boolean {
        val id = selection.widgetId ?: return false
        return perform(AbyssusBundle.message("uiCommandMoveWidget")) { e, m -> e.reorderWidget(m, id, newIndex) } is UiEditOutcome.Edited
    }

    /** The selected list gets an empty state (or loses it). Selecting a widget inside the list's row or empty state also works. */
    fun addListEmpty(): Boolean {
        val list = enclosingList() ?: return false
        return perform(AbyssusBundle.message("uiCommandListEmpty")) { e, m -> e.addListEmpty(m, list) } is UiEditOutcome.Edited
    }

    fun removeListEmpty(): Boolean {
        val list = enclosingList() ?: return false
        return perform(AbyssusBundle.message("uiCommandListEmpty")) { e, m -> e.removeListEmpty(m, list) } is UiEditOutcome.Edited
    }

    /** The list the selection is, or is inside of (its row or empty state); null when there is none. */
    private fun enclosingList(): String? {
        val c = collection ?: return null
        val tree = UiTree(c)
        var at = selection.widgetId?.let(tree::widget)
        while (at != null) {
            if (at.type == net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType.LIST) return at.id
            at = tree.parentOf(at.id)
        }
        return null
    }

    /** Up and Down move focus on [screenId] (`true`), or only Tab does (`false`, the default). */
    fun setNavigation(screenId: String, arrows: Boolean) =
        perform(AbyssusBundle.message("uiCommandNavigation")) { e, m -> e.setScreenNavigation(m, screenId, if (arrows) "arrows" else "tab") } is UiEditOutcome.Edited

    fun addScreen(name: String, kind: String): Boolean {
        val id = added(perform(AbyssusBundle.message("uiCommandAddScreen")) { e, m -> e.addScreen(m, name, kind) }) ?: return false
        selectScreen(id)
        return true
    }

    fun renameScreen(id: String, name: String) = perform(AbyssusBundle.message("uiCommandRenameScreen")) { e, m -> e.renameScreen(m, id, name) } is UiEditOutcome.Edited

    fun deleteScreen(id: String) = perform(AbyssusBundle.message("uiCommandDeleteScreen")) { e, m -> e.deleteScreen(m, id) } is UiEditOutcome.Edited

    /** A generic property-panel edit; see `UiDocumentEdits`. */
    fun edit(name: String, op: (UiDocumentEdits, JsonNode) -> UiEditResult): Boolean = perform(name, null, op) is UiEditOutcome.Edited

    // ---- canvas gestures ----

    private val gestures = UiCanvasGestures(
        { collection }, { text }, { layoutProvider(it) }, ::select,
        { baseText, edit -> perform(AbyssusBundle.message("uiCommandDrag"), baseText, edit) is UiEditOutcome.Edited },
    )

    override val gestureActive: Boolean get() = gestures.active

    override val selectedWidgetId: String? get() = selection.widgetId

    /** A press on [widgetId] in the edit canvas: selects it and starts a drag at viewport point ([x], [y]). */
    override fun beginGesture(widgetId: String, x: Float, y: Float) = gestures.begin(widgetId, x, y)

    /** Transient preview only; nothing is written. */
    override fun updateGesture(x: Float, y: Float): UiGesturePreview = gestures.update(x, y)

    /** The release: one command for the whole drag, refused when the document changed since the press. */
    override fun endGesture(x: Float, y: Float): Boolean = gestures.end(x, y)

    fun cancelGesture() = gestures.cancel()

    override fun dispose() {
        disposed = true
        listeners.clear()
        zoomListeners.clear()
        gestures.cancel()
    }
}
