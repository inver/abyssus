/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import net.nevinsky.abyssus.plugin.assetfiles.AssetEditResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetMetaEdits
import net.nevinsky.abyssus.lib.core.editor.panel.FieldRef
import net.nevinsky.abyssus.lib.core.editor.panel.PanelBuilders
import net.nevinsky.abyssus.plugin.projectView.assetUnusedOf
import net.nevinsky.abyssus.plugin.panel.RowHooks
import net.nevinsky.abyssus.plugin.ui.CommitOutcome
import net.nevinsky.abyssus.plugin.ui.choiceEditor
import net.nevinsky.abyssus.plugin.ui.textEditor
import com.intellij.openapi.components.service
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings
import net.nevinsky.abyssus.plugin.filetype.ProjectSettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.meta.*
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.filetype.AssetIcons
import net.nevinsky.abyssus.plugin.foliage.FoliageController
import net.nevinsky.abyssus.plugin.foliage.FoliageDrafts
import net.nevinsky.abyssus.plugin.foliage.FoliageSection
import net.nevinsky.abyssus.plugin.foliage.FoliageSource
import net.nevinsky.abyssus.plugin.foliage.FoliageUndoCache
import net.nevinsky.abyssus.plugin.foliage.foliageUnusableNote
import net.nevinsky.abyssus.plugin.projectView.*
import net.nevinsky.abyssus.plugin.schema.ComponentSchemasListener
import net.nevinsky.abyssus.plugin.terrain.TerrainGenerationController
import net.nevinsky.abyssus.plugin.terrain.TerrainGenerationSection
import net.nevinsky.abyssus.plugin.terrain.TerrainSource
import net.nevinsky.abyssus.plugin.terrain.terrainUnusableNote
import java.awt.*
import javax.swing.*

/**
 * The Abyssus Properties panel: the Meta of the asset selected in the Abyssus view (read only), or the components of the
 * selected entity, whose fields can be edited. [background] and [ui] say
 * where the asset is read and where the result is shown, so tests can run both inline.
 */
class AssetPropertiesPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
    private val services: PanelServices,
    private val background: (Runnable) -> Unit = { AppExecutorUtil.getAppExecutorService().execute(it) },
    private val ui: (Runnable) -> Unit = { ApplicationManager.getApplication().invokeLater(it, ModalityState.any()) },
) : JPanel(CardLayout()), UiDataProvider {
    private val projectSettings = project.service<AbyssusProjectSettings>()
    private var projectFile: VirtualFile? = null
    private val cards = layout as CardLayout
    private val content = JPanel(BorderLayout())
    private val empty = JPanel(GridBagLayout())

    private var selected: Any? = null
    private var folder: VirtualFile? = null
    private var scene: VirtualFile? = null
    private var generation = 0
    private var viewDisposable: Disposable? = null
    private var disposed = false

    /**
     * A text editor on the shown asset's `meta.json`, never displayed: it is what the platform's Undo and Redo act on, so
     * they work with focus in this panel, and what the panel's own Undo and Redo buttons use.
     */
    private var undoEditor: TextEditor? = null
    private var undoFile: VirtualFile? = null

    /** The regeneration controls' logic for the terrain shown; kept across refreshes of the same terrain so a draft survives them. */
    private var terrainController: TerrainGenerationController? = null
    private var terrainFolder: VirtualFile? = null

    /** The foliage section's logic for the asset shown; kept across refreshes of the same asset so a draft survives them. */
    private var foliageController: FoliageController? = null
    private var foliageFolder: VirtualFile? = null
    private var rendering = false

    /** The property whose edit was rejected as stale: its next row says so, because the refresh rebuilds the row. */
    private var conflictKey: String? = null

    /** The sections the user collapsed on the node shown, by section key; cleared when the selection moves to another node. */
    private val collapsedSections = HashMap<String, Boolean>()

    /** What the panel currently shows. */
    internal var state: PanelState = emptyState(null)
        private set

    init {
        add(content, DETAILS)
        add(empty, EMPTY)
        project.messageBus.connect(parentDisposable)
            .subscribe(AbyssusSelectionListener.TOPIC, AbyssusSelectionListener { show(it) })
        project.messageBus.connect(parentDisposable)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { touches(it.file) }) ui { if (!disposed) refresh() }
                }
            })
        // a changed component schema turns a scene's components editable or read only
        project.messageBus.connect(parentDisposable).subscribe(
            ComponentSchemasListener.TOPIC,
            ComponentSchemasListener { ui { if (!disposed && scene != null) refresh() } })
        project.messageBus.connect(parentDisposable).subscribe(ProjectSettingsListener.TOPIC,
            ProjectSettingsListener { file, _ -> if (file == projectFile) ui { if (!disposed) refresh() } })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (touches(FileDocumentManager.getInstance().getFile(event.document))) refresh()
            }
        }, parentDisposable)
        Disposable {
            disposed = true; useUndoEditor(null); useTerrain(null); useFoliage(null)
        }.also { com.intellij.openapi.util.Disposer.register(parentDisposable, it) }
        show(AbyssusSelection.of(project).current)
    }

    /** True when [file] is the shown asset's folder or a file in it (its `meta.json` or a face image). */
    private fun touches(file: VirtualFile?): Boolean {
        if (file == null) return false
        if (file == scene || file == projectFile) return true
        val shown = folder ?: return false
        return file == shown || file.parent == shown
    }

    private fun refresh() = show(selected)

    override fun uiDataSnapshot(sink: DataSink) {
        undoEditor?.let { sink[PlatformCoreDataKeys.FILE_EDITOR] = it }
    }

    private fun useUndoEditor(file: VirtualFile?) {
        if (file == undoFile && (file == null || undoEditor != null)) return
        undoEditor?.let(com.intellij.openapi.util.Disposer::dispose)
        undoEditor = null
        undoFile = file
        if (file == null || !file.isValid) return
        undoEditor = runCatchingKeepingCancellation {
            TextEditorProvider.getInstance().createEditor(project, file) as? TextEditor
        }.getOrNull()
    }

    /** Shows the asset in [assetFolder] directly, without a tree row; for tests whose files must be on disk. */
    internal fun showFolder(assetFolder: VirtualFile) = show(assetFolder)

    /** Shows [node]: an asset row's Meta, else the empty state. Reads the asset in the background. */
    fun show(node: Any?) {
        if (node != selected) collapsedSections.clear()
        selected = node
        val token = ++generation
        val assetFolder = (node as? VirtualFile)?.takeIf { it.isDirectory } ?: assetFolderOf(node)
        selectedUnused = assetUnusedOf(node)
        folder = assetFolder
        if (assetFolder != terrainFolder) useTerrain(null) // another selection: its draft and pending preview are discarded
        if (assetFolder != foliageFolder) useFoliage(null)
        val entity = if (assetFolder == null) componentTargetOf(node) else null
        val water = if (assetFolder == null && entity == null) waterTargetOf(node) else null
        val structural = if (assetFolder == null && entity == null && water == null) structuralOf(node) else null
        scene = entity?.file ?: water?.file ?: when (structural) {
            is Structural.Setting -> structural.file
            is Structural.Ecs -> structural.file
            else -> null
        }
        projectFile = null
        if (structural != null) {
            background {
                val result = readStructuralState(structural)
                ui { if (token == generation && !disposed) apply(result) }
            }
            return
        }
        if (water != null) {
            background {
                val result = readWaterState(water)
                ui { if (token == generation && !disposed) apply(result) }
            }
            return
        }
        if (entity != null) {
            background {
                val result = readEntityState(entity, services)
                ui { if (token == generation && !disposed) apply(result) }
            }
            return
        }
        if (assetFolder == null) {
            val selectedFile = (node as? AbyssusAssetNode)?.virtualFile ?: node as? VirtualFile
            if (selectedFile?.isValid == true && selectedFile.extension == "abss") {
                projectFile = selectedFile
                background {
                    val result = readProjectState(selectedFile, selectedFile.name, projectSettings)
                    ui { if (token == generation && !disposed) apply(result) }
                }
                return
            }
            val sceneFile = viewableSceneFile(node)?.takeIf { it.isValid && ProjectLayout.isScene(it) }
            if (sceneFile != null) {
                scene = sceneFile
                background {
                    val result = readSceneState(sceneFile, describeNonAsset(node)?.first ?: sceneFile.name)
                    ui { if (token == generation && !disposed) apply(result) }
                }
            } else {
                apply(
                    if (node.isAssetRow()) emptyState(null) else emptyState(node)
                )
            }
            return
        }
        background {
            val result = readAssetState(assetFolder, services)
            ui { if (token == generation && !disposed) apply(result) }
        }
    }

    private fun Any?.isAssetRow() = (this as? DtoEntryNode)?.value?.value is Asset<*>

    /** Keeps the controller of the terrain in [details] (a new one for another terrain), or drops it for none. */
    private fun useTerrain(details: PanelState.Details?) {
        val ready = details?.terrain as? TerrainSource.Ready
        val folder = details?.meta?.folder
        if (ready == null || folder == null) {
            terrainController?.dispose()
            terrainController = null
            terrainFolder = null
            return
        }
        val existing = terrainController
        if (existing != null && terrainFolder == folder) {
            existing.sourceRead(ready)
            return
        }
        existing?.dispose()
        terrainFolder = folder
        terrainController = TerrainGenerationController(
            project,
            folder,
            ready,
            services.terrainGenerator,
            services.heightEncoder,
            services.terrainRecipes,
            services.terrainSculpt,
            background,
            ui,
            readCurrent = { readTerrainSourceNow(folder, services) },
        ).also { controller -> controller.onChange = { if (!rendering && !disposed) apply(state) } }
    }

    /** Keeps the controller of the foliage asset in [details] (a new one for another asset), or drops it for none. */
    private fun useFoliage(details: PanelState.Details?) {
        val ready = details?.foliage as? FoliageSource.Ready
        val folder = details?.meta?.folder
        if (ready == null || folder == null) {
            foliageController?.dispose()
            foliageController = null
            foliageFolder = null
            return
        }
        val existing = foliageController
        if (existing != null && foliageFolder == folder) {
            existing.sourceRead(ready)
            return
        }
        existing?.dispose()
        foliageFolder = folder
        foliageController = FoliageController(
            project,
            folder,
            ready,
            project.service<FoliageDrafts>(),
            services.json,
            project.service<FoliageUndoCache>(),
            background,
            ui,
            readCurrent = { readFoliageSourceNow(folder, services) },
        ).also { controller -> controller.onChange = { if (!rendering && !disposed) apply(state) } }
    }

    private fun apply(newState: PanelState) {
        state = newState
        // a scene's switch listens to the Scene views while it is shown; the listener goes with the view
        viewDisposable?.let(com.intellij.openapi.util.Disposer::dispose)
        viewDisposable = null
        rendering = true
        try {
            render(newState)
        } finally {
            rendering = false
        }
    }

    private fun render(newState: PanelState) {
        when (newState) {
            is PanelState.Project -> {
                useTerrain(null)
                useFoliage(null)
                useUndoEditor(newState.file)
                val own = com.intellij.openapi.util.Disposer.newDisposable(parentDisposable, "project-details")
                    .also { viewDisposable = it }
                content.removeAll()
                content.add(scrolled(ProjectDetailsView(newState, projectSettings, project.service<net.nevinsky.abyssus.plugin.sceneview.SceneViewSettings>(), own, collapsedSections)).apply {
                    border = BorderFactory.createEmptyBorder()
                }, BorderLayout.CENTER)
                cards.show(this, DETAILS)
            }

            is PanelState.UISceneState -> {
                useTerrain(null)
                useFoliage(null)
                useUndoEditor(newState.file)
                val own = com.intellij.openapi.util.Disposer.newDisposable(parentDisposable, "scene-details")
                    .also { viewDisposable = it }
                content.removeAll()
                val conflict = conflictKey.also { conflictKey = null }
                val view = SceneDetailsView(services.rayControls, newState, own, conflict, onConflict = { conflictKey = it }, collapsed = collapsedSections)
                content.add(
                    scrolled(view).apply { border = BorderFactory.createEmptyBorder() },
                    BorderLayout.CENTER
                )
                cards.show(this, DETAILS)
            }

            is PanelState.Empty -> {
                useTerrain(null)
                useFoliage(null)
                useUndoEditor(null)
                fillEmpty(newState)
                cards.show(this, EMPTY)
            }

            is PanelState.WaterDetails -> {
                useTerrain(null)
                useFoliage(null)
                useUndoEditor(newState.target.file)
                content.removeAll()
                content.add(scrolled(WaterPropertiesView(project, newState, collapsedSections)).apply {
                    border = BorderFactory.createEmptyBorder()
                }, BorderLayout.CENTER)
                cards.show(this, DETAILS)
            }

            is PanelState.EntityDetails -> {
                useTerrain(null)
                useFoliage(null)
                useUndoEditor(newState.target.file)
                content.removeAll()
                content.add(scrolled(EntityDetailsView(project, newState, services.metaFiles, collapsedSections)).apply {
                    border = BorderFactory.createEmptyBorder()
                }, BorderLayout.CENTER)
                cards.show(this, DETAILS)
            }

            is PanelState.StructuralDetails -> {
                useTerrain(null)
                useFoliage(null)
                val file = (newState.selection as? Structural.Setting)?.file ?: (newState.selection as? Structural.Ecs)?.file
                useUndoEditor(file)
                content.removeAll()
                content.add(scrolled(StructuralView(project, newState, collapsedSections)).apply {
                    border = BorderFactory.createEmptyBorder()
                }, BorderLayout.CENTER)
                cards.show(this, DETAILS)
            }

            is PanelState.Details -> {
                useTerrain(newState)
                useFoliage(newState)
                // a foliage asset has no editable fields, but its section edits the same `meta.json`
                useUndoEditor(
                    if (newState.fields.isEmpty() && newState.foliage == null) null
                    else newState.meta.folder.findChild(META_FILE)
                )
                content.removeAll()
                content.add(
                    scrolled(details(newState)).apply { border = BorderFactory.createEmptyBorder() },
                    BorderLayout.CENTER
                )
                cards.show(this, DETAILS)
            }
        }
        revalidate()
        repaint()
    }

    /** [view] in a scroll pane that follows the width of the tool window. */
    private fun scrolled(view: JComponent) = JBScrollPane(net.nevinsky.abyssus.plugin.panel.WidthTrackingPanel(view)).apply {
        horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    private fun fillEmpty(e: PanelState.Empty) {
        empty.removeAll()
        val box = JPanel(VerticalLayout(JBUI.scale(10)))
        box.isOpaque = false
        box.add(JBLabel(AssetIcons.UNKNOWN, SwingConstants.CENTER).apply {
            horizontalAlignment = SwingConstants.CENTER
        })
        box.add(JBLabel(e.message, SwingConstants.CENTER).apply { horizontalAlignment = SwingConstants.CENTER })
        e.hint?.let {
            box.add(JBLabel("<html><center>${it}</center></html>", SwingConstants.CENTER).apply {
                foreground = secondary(); horizontalAlignment = SwingConstants.CENTER
            })
        }
        empty.add(box, GridBagConstraints().apply { weightx = 1.0; weighty = 1.0; insets = JBUI.insets(24) })
    }

    /** The asset the selection stands for is used by no scene, which the header says with a badge. */
    private var selectedUnused = false

    private fun details(d: PanelState.Details): JComponent {
        val editable = d.fields.map { it.key }.toSet()
        val hasEditors = d.fields.isNotEmpty() || d.foliage != null
        val model = PanelBuilders(EditorBundle, { false }).asset(
            d.name, d.meta.json, !selectedUnused, editable, d.meta.folder.parent?.parent?.children?.firstOrNull { it.extension == ProjectLayout.PROJECT_EXTENSION }?.name.orEmpty(), hasEditors,
        )
        val hooks = object : RowHooks {
            override fun commit(ref: FieldRef, text: String): CommitOutcome = CommitOutcome.Unchanged
            override fun openScreenEditor() = openScreen(d)
            override fun assetField(key: String, label: String): JComponent? =
                d.fields.firstOrNull { it.key == key }?.let { fieldRow(it, label) }

            override fun custom(key: String): JComponent? = when (key) {
                "terrain" -> when (val terrain = d.terrain) {
                    is TerrainSource.Unusable -> terrainUnusableNote(terrain.reason)
                    is TerrainSource.Ready -> terrainController?.let { TerrainGenerationSection(it) }
                    null -> null
                }

                "foliage" -> when (val foliage = d.foliage) {
                    is FoliageSource.Unusable -> foliageUnusableNote(foliage.reason)
                    is FoliageSource.Ready -> foliageController?.let { FoliageSection(it, undoButtons()) }
                    null -> null
                }

                "faces" -> d.faces?.let { facePreviews(it) }
                "hdr" -> d.hdr?.let { hdrPreview(it) }
                else -> null
            }
        }
        return PanelModelView(
            model, hooks, collapsedSections, headerExtra = if (hasEditors) undoButtons() else null,
        )
    }

    /** Opens the Screen Editor on the UI asset shown. */
    private fun openScreen(d: PanelState.Details) {
        val meta = d.meta.folder.findChild(META_FILE) ?: return
        project.service<net.nevinsky.abyssus.plugin.projectView.UiScreenOpener>().open(meta, null)
    }

    /** Undo and Redo for edits made here: the platform commands on this asset's `meta.json`. */
    private fun undoButtons(): JComponent {
        val manager = UndoManager.getInstance(project)
        val editor = undoEditor
        fun button(key: String, label: String, available: () -> Boolean, run: () -> Unit) = JButton(label).apply {
            name = key
            isEnabled = editor != null && available()
            addActionListener { run() }
        }
        return JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(
                button(
                    "asset-undo",
                    AbyssusBundle.message("propertiesUndo"),
                    { manager.isUndoAvailable(editor) }) { manager.undo(editor); refresh() })
            add(
                button(
                    "asset-redo",
                    AbyssusBundle.message("propertiesRedo"),
                    { manager.isRedoAvailable(editor) }) { manager.redo(editor); refresh() })
        }
    }

    /** The editor of one supported property; a refused value goes back to what the file holds, with the reason beside it. */
    private fun fieldRow(state: AssetFieldState, label: String): JComponent {
        val error = JBLabel("").apply { foreground = JBColor.RED; name = "asset-error-${state.key}" }
        if (conflictKey == state.key) {
            conflictKey = null
            error.text = EditorBundle.message("assetFieldConflict")
        }
        val editor = fieldEditor(state, error)
        editor.name = "asset-field-${state.key}"
        val editorAndError = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(editor, BorderLayout.CENTER)
            add(error, BorderLayout.EAST)
        }
        return net.nevinsky.abyssus.plugin.panel.fieldRow(label, editorAndError)
    }

    private fun fieldEditor(state: AssetFieldState, error: JBLabel): JComponent {
        if (state.field.kind == FieldKind.ASSET_REFERENCE || state.field.kind == FieldKind.LOCAL_FILE) {
            val selected = state.choices.firstOrNull { it.value == (state.value as? FieldValue.Text)?.value }
            return choiceEditor(
                state.choices, selected, error, SimpleListCellRenderer.create("") { choiceLabel(it) },
            ) { chosen ->
                commitField(state, chosen.value?.let { FieldValue.Text(it) } ?: FieldValue.None)
            }
        }
        val field = textEditor(state.text, error) { text ->
            when (val parsed = services.assetEditor.parse(state.field, text)) {
                is ParseOutcome.Failed -> CommitOutcome.Refused(editErrorMessage(parsed.error))
                is ParseOutcome.Parsed -> commitField(state, parsed.value)
            }
        }
        field.font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
        return field
    }

    /** Writes [value] for [state]'s property; the editor shows a refusal and puts the stored value back. */
    private fun commitField(state: AssetFieldState, value: FieldValue): CommitOutcome {
        val folder = this.folder ?: return CommitOutcome.Unchanged
        if (value == state.value) return CommitOutcome.Unchanged
        return when (val result =
            AssetMetaEdits.update(project, folder, state.key, state.value, value, services.assetEditor)) {
            AssetEditResult.Changed -> {
                refresh() // the document listener ran inside the command, before Undo became available
                CommitOutcome.Saved
            }

            AssetEditResult.Unchanged -> CommitOutcome.Unchanged
            is AssetEditResult.Rejected -> CommitOutcome.Refused(editErrorMessage(result.error))
            is AssetEditResult.Conflict -> {
                conflictKey = state.key
                refresh()
                CommitOutcome.Refused(EditorBundle.message("assetFieldConflict"))
            }

            AssetEditResult.Unreadable -> CommitOutcome.Refused(AbyssusBundle.message("assetFieldUnreadable"))
        }
    }

    private fun secondary(): Color = secondaryColor()

    companion object {
        private const val DETAILS = "details"
        private const val EMPTY = "empty"
    }
}
