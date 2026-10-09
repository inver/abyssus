/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

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
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.lib.gdx.assets.Asset
import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.gdx.editor.meta.*
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.filetype.AssetIcons
import net.nevinsky.abyssus.plugin.projectView.*
import net.nevinsky.abyssus.plugin.schema.ComponentSchemasListener
import net.nevinsky.abyssus.plugin.terrain.TerrainGenerationController
import net.nevinsky.abyssus.plugin.terrain.TerrainGenerationSection
import net.nevinsky.abyssus.plugin.terrain.TerrainSource
import net.nevinsky.abyssus.plugin.terrain.terrainUnusableNote
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.image.BufferedImage
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
    private var rendering = false

    /** The property whose edit was rejected as stale: its next row says so, because the refresh rebuilds the row. */
    private var conflictKey: String? = null

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
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (touches(FileDocumentManager.getInstance().getFile(event.document))) refresh()
            }
        }, parentDisposable)
        Disposable {
            disposed = true; useUndoEditor(null); useTerrain(null)
        }.also { com.intellij.openapi.util.Disposer.register(parentDisposable, it) }
        show(AbyssusSelection.of(project).current)
    }

    /** True when [file] is the shown asset's folder or a file in it (its `meta.json` or a face image). */
    private fun touches(file: VirtualFile?): Boolean {
        if (file == null) return false
        if (file == scene) return true
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
        selected = node
        val token = ++generation
        val assetFolder = (node as? VirtualFile)?.takeIf { it.isDirectory } ?: assetFolderOf(node)
        folder = assetFolder
        if (assetFolder != terrainFolder) useTerrain(null) // another selection: its draft and pending preview are discarded
        val entity = if (assetFolder == null) componentTargetOf(node) else null
        scene = entity?.file
        if (entity != null) {
            background {
                val result = readEntityState(entity, services)
                ui { if (token == generation && !disposed) apply(result) }
            }
            return
        }
        if (assetFolder == null) {
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
            background,
            ui,
            readCurrent = { readTerrainSourceNow(folder, services) },
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
            is PanelState.UISceneState -> {
                useTerrain(null)
                useUndoEditor(newState.file)
                val own = com.intellij.openapi.util.Disposer.newDisposable(parentDisposable, "scene-details")
                    .also { viewDisposable = it }
                content.removeAll()
                val conflict = conflictKey.also { conflictKey = null }
                val view = SceneDetailsView(services.rayControls, newState, own, conflict) { conflictKey = it }
                content.add(
                    JBScrollPane(view).apply { border = BorderFactory.createEmptyBorder() },
                    BorderLayout.CENTER
                )
                cards.show(this, DETAILS)
            }

            is PanelState.Empty -> {
                useTerrain(null)
                useUndoEditor(null)
                fillEmpty(newState)
                cards.show(this, EMPTY)
            }

            is PanelState.EntityDetails -> {
                useTerrain(null)
                useUndoEditor(newState.target.file)
                content.removeAll()
                content.add(JBScrollPane(EntityDetailsView(project, newState, services.metaFiles)).apply {
                    border = BorderFactory.createEmptyBorder()
                }, BorderLayout.CENTER)
                cards.show(this, DETAILS)
            }

            is PanelState.Details -> {
                useTerrain(newState)
                useUndoEditor(if (newState.fields.isEmpty()) null else newState.meta.folder.findChild(META_FILE))
                content.removeAll()
                content.add(
                    JBScrollPane(details(newState)).apply { border = BorderFactory.createEmptyBorder() },
                    BorderLayout.CENTER
                )
                cards.show(this, DETAILS)
            }
        }
        revalidate()
        repaint()
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

    private fun details(d: PanelState.Details): JComponent {
        val box = JPanel(VerticalLayout(0))
        box.add(header(d))
        box.add(columnHeader())
        val rows = JPanel(VerticalLayout(0)).apply { border = JBUI.Borders.empty(4, 0) }
        // supported fields a file omits (procedural sky defaults) are listed with their effective value
        for (row in detailRows(d.meta.rows, d.fields)) rows.add(
            when (row) {
                is DetailRow.Field -> fieldRow(d, row.state)
                is DetailRow.Plain -> rowOf(row.row)
            }
        )
        box.add(rows)
        when (val terrain = d.terrain) {
            is TerrainSource.Unusable -> box.add(terrainUnusableNote(terrain.reason))
            is TerrainSource.Ready -> terrainController?.let { box.add(TerrainGenerationSection(it)) }
            null -> {}
        }
        d.faces?.let { box.add(previews(it)) }
        d.hdr?.let { box.add(hdrPreview(it)) }
        return JPanel(BorderLayout()).apply { add(box, BorderLayout.NORTH) }
    }

    private fun header(d: PanelState.Details): JComponent {
        val text = JPanel(VerticalLayout(JBUI.scale(2))).apply {
            add(JBLabel(d.name).apply { font = JBFont.label().asBold().biggerOn(1f) })
            val type = (d.meta.json.get("type")?.takeIf { it.isTextual }?.asText()
                ?: AbyssusBundle.message("propertiesUnknownType")).lowercase() // as the file spells it
            if (d.fields.isEmpty()) {
                add(JBLabel(AbyssusBundle.message("propertiesSubtitle", type)).apply { foreground = secondary() })
            } else {
                add(JBLabel(AbyssusBundle.message("propertiesSubtitleEditable", type)).apply {
                    foreground = secondary()
                })
                add(JBLabel(AbyssusBundle.message("propertiesSharedNote")).apply {
                    foreground = secondary(); font = JBFont.small(); name = "shared-asset-note"
                })
            }
        }
        return JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(12, 16)
            )
            add(JBLabel(AssetIcons.forType(d.meta.type)), BorderLayout.WEST)
            add(text, BorderLayout.CENTER)
            if (d.fields.isNotEmpty()) add(undoButtons(), BorderLayout.EAST)
        }
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

    private fun columnHeader(): JComponent = twoColumns(
        JBLabel(AbyssusBundle.message("propertiesNameColumn").uppercase()).apply {
            foreground = secondary(); font = JBFont.small()
        },
        JBLabel(AbyssusBundle.message("propertiesValueColumn").uppercase()).apply {
            foreground = secondary(); font = JBFont.small()
        },
    ).apply {
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(6, 16)
        )
    }

    private fun rowOf(row: PropertyRow): JComponent = when (row.kind) {
        RowKind.HEADING -> twoColumns(JBLabel(row.name).apply { foreground = ACCENT }, JBLabel("")).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(10, 16, 4, 16)
            )
        }

        else -> {
            val indent = if (row.kind == RowKind.ADDITIONAL) "    " else ""
            val value = JBLabel(row.value).apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size); toolTipText =
                row.value.ifEmpty { null }
            }
            twoColumns(JBLabel(indent + row.name), value).apply { border = JBUI.Borders.empty(4, 16) }
        }
    }

    /** The editor of one supported property; a refused value goes back to what the file holds, with the reason beside it. */
    private fun fieldRow(d: PanelState.Details, state: AssetFieldState): JComponent {
        val error = JBLabel("").apply { foreground = JBColor.RED; name = "asset-error-${state.key}" }
        if (conflictKey == state.key) {
            conflictKey = null
            error.text = EditorBundle.message("assetFieldConflict")
        }
        val editor = fieldEditor(d, state, error)
        editor.name = "asset-field-${state.key}"
        val editorAndError = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(editor, BorderLayout.CENTER)
            add(error, BorderLayout.EAST)
        }
        return twoColumns(JBLabel("    " + state.key), editorAndError).apply { border = JBUI.Borders.empty(4, 16) }
    }

    private fun fieldEditor(d: PanelState.Details, state: AssetFieldState, error: JBLabel): JComponent {
        if (state.field.kind == FieldKind.ASSET_REFERENCE || state.field.kind == FieldKind.LOCAL_FILE) {
            val combo = ComboBox(state.choices.toTypedArray())
            combo.renderer = SimpleListCellRenderer.create("") { choiceLabel(it) }
            combo.selectedItem = state.choices.firstOrNull { it.value == (state.value as? FieldValue.Text)?.value }
            combo.addActionListener {
                val chosen = combo.selectedItem as? AssetChoice ?: return@addActionListener
                val value = chosen.value?.let { FieldValue.Text(it) } ?: FieldValue.None
                if (value != state.value) commitField(d, state, value, error) {
                    combo.selectedItem =
                        state.choices.firstOrNull { it.value == (state.value as? FieldValue.Text)?.value }
                }
            }
            return combo
        }
        val field = JBTextField(state.text)
        field.font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
        val save = save@{
            if (field.text == state.text) return@save
            when (val parsed = services.assetEditor.parse(state.field, field.text)) {
                is ParseOutcome.Failed -> {
                    error.text = editErrorMessage(parsed.error)
                    field.text = state.text
                }

                is ParseOutcome.Parsed -> commitField(d, state, parsed.value, error) { field.text = state.text }
            }
        }
        field.addActionListener { save() }
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = save()
        })
        return field
    }

    private fun commitField(
        d: PanelState.Details,
        state: AssetFieldState,
        value: FieldValue,
        error: JBLabel,
        revert: () -> Unit
    ) {
        val folder = this.folder ?: return
        when (val result =
            AssetMetaEdits.update(project, folder, state.key, state.value, value, services.assetEditor)) {
            AssetEditResult.Changed -> {
                error.text = ""
                refresh() // the document listener ran inside the command, before Undo became available
            }

            AssetEditResult.Unchanged -> {
                error.text = ""
                revert()
            }

            is AssetEditResult.Rejected -> {
                error.text = editErrorMessage(result.error)
                revert()
            }

            is AssetEditResult.Conflict -> {
                error.text = EditorBundle.message("assetFieldConflict")
                revert()
                conflictKey = state.key
                refresh()
            }

            AssetEditResult.Unreadable -> {
                error.text = AbyssusBundle.message("assetFieldUnreadable")
                revert()
            }
        }
    }

    private fun twoColumns(name: JComponent, value: JComponent): JPanel = JPanel(GridBagLayout()).apply {
        add(
            name,
            GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.NORTHWEST; ipadx = 0 }
                .also { name.preferredSize = Dimension(JBUI.scale(NAME_COLUMN), name.preferredSize.height) })
        add(
            value,
            GridBagConstraints().apply {
                gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.NORTHWEST
            })
    }

    private fun previews(faces: List<FaceCell>): JComponent {
        val grid = JPanel(GridLayout(0, 3, JBUI.scale(10), JBUI.scale(10)))
        for (f in faces) grid.add(faceCell(f))
        return JPanel(VerticalLayout(JBUI.scale(8))).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(12, 16, 16, 16)
            )
            add(JBLabel(AbyssusBundle.message("propertiesFacePreviews").uppercase()).apply {
                foreground = secondary(); font = JBFont.small()
            })
            add(grid)
        }
    }

    private fun hdrPreview(cell: HdrCell): JComponent = JPanel(VerticalLayout(JBUI.scale(8))).apply {
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(12, 16, 16, 16)
        )
        add(JBLabel(AbyssusBundle.message("propertiesHdrPreview").uppercase()).apply {
            foreground = secondary(); font = JBFont.small()
        })
        add(Thumbnail(cell.image).apply {
            name = "hdr-preview"
            preferredSize = Dimension(JBUI.scale(288), JBUI.scale(144))
        })
        add(JBLabel(cell.label).apply {
            foreground = secondary(); font = Font(Font.MONOSPACED, Font.PLAIN, JBFont.small().size)
        })
    }

    private fun faceCell(f: FaceCell): JComponent {
        val caption = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            add(JBLabel(f.face), BorderLayout.WEST)
            add(JBLabel(f.file).apply {
                foreground = secondary(); font =
                Font(Font.MONOSPACED, Font.PLAIN, JBFont.small().size); horizontalAlignment = SwingConstants.RIGHT
            }, BorderLayout.CENTER)
        }
        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(Thumbnail(f.image).apply { name = "face-${f.face}" }, BorderLayout.CENTER)
            add(caption, BorderLayout.SOUTH)
        }
    }

    private fun secondary(): Color = UIUtil.getContextHelpForeground()

    companion object {
        private const val DETAILS = "details"
        private const val EMPTY = "empty"
        private const val NAME_COLUMN = 150

        /** The accent of the design canvas (`#3fb8c9` on dark), darkened for light themes. */
        private val ACCENT = JBColor(Color(0x1E8A99), Color(0x3FB8C9))
    }
}

/** A face thumbnail fitted into its cell; a bordered blank cell when the image is missing. */
internal class Thumbnail(val image: BufferedImage?) : JComponent() {
    init {
        preferredSize = Dimension(JBUI.scale(96), JBUI.scale(72))
    }

    override fun paintComponent(g: Graphics) {
        val img = image
        if (img == null) {
            g.color = JBColor.border()
            g.drawRect(0, 0, width - 1, height - 1)
            return
        }
        val ratio = minOf(width.toDouble() / img.width, height.toDouble() / img.height)
        val w = (img.width * ratio).toInt()
        val h = (img.height * ratio).toInt()
        g.drawImage(img, (width - w) / 2, (height - h) / 2, w, h, null)
    }
}
