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

package net.nevinsky.abyssus.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.filetype.AssetIcons
import net.nevinsky.abyssus.projectView.AbyssusSelection
import net.nevinsky.abyssus.projectView.AbyssusSelectionListener
import net.nevinsky.abyssus.projectView.DtoEntryNode
import net.nevinsky.abyssus.projectView.assetFolderOf
import net.nevinsky.abyssus.projectView.componentTargetOf
import net.nevinsky.abyssus.dto.AssetInfo
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * The Abyssus Properties panel: the Meta of the asset selected in the Abyssus view (read only), or the components of the
 * selected entity, whose fields can be edited. [background] and [ui] say
 * where the asset is read and where the result is shown, so tests can run both inline.
 */
class AssetPropertiesPanel(
    private val project: Project,
    parent: Disposable,
    private val background: (Runnable) -> Unit = { AppExecutorUtil.getAppExecutorService().execute(it) },
    private val ui: (Runnable) -> Unit = { ApplicationManager.getApplication().invokeLater(it, ModalityState.any()) },
) : JPanel(CardLayout()) {
    private val cards = layout as CardLayout
    private val content = JPanel(BorderLayout())
    private val empty = JPanel(GridBagLayout())

    private var selected: Any? = null
    private var folder: VirtualFile? = null
    private var scene: VirtualFile? = null
    private var generation = 0
    private var disposed = false

    /** What the panel currently shows. */
    internal var state: PanelState = emptyState(null)
        private set

    init {
        add(content, DETAILS)
        add(empty, EMPTY)
        project.messageBus.connect(parent).subscribe(AbyssusSelectionListener.TOPIC, AbyssusSelectionListener { show(it) })
        project.messageBus.connect(parent).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { touches(it.file) }) ui { if (!disposed) refresh() }
            }
        })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (touches(FileDocumentManager.getInstance().getFile(event.document))) refresh()
            }
        }, parent)
        Disposable { disposed = true }.also { com.intellij.openapi.util.Disposer.register(parent, it) }
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

    /** Shows [node]: an asset row's Meta, else the empty state. Reads the asset in the background. */
    fun show(node: Any?) {
        selected = node
        val token = ++generation
        val assetFolder = assetFolderOf(node)
        folder = assetFolder
        val entity = if (assetFolder == null) componentTargetOf(node) else null
        scene = entity?.file
        if (entity != null) {
            background {
                val result = readEntityState(entity)
                ui { if (token == generation && !disposed) apply(result) }
            }
            return
        }
        if (assetFolder == null) {
            apply(if (node.isAssetRow()) emptyState(null) else emptyState(node))
            return
        }
        background {
            val result = readAssetState(assetFolder)
            ui { if (token == generation && !disposed) apply(result) }
        }
    }

    private fun Any?.isAssetRow() = (this as? DtoEntryNode)?.value?.value is AssetInfo

    private fun apply(newState: PanelState) {
        state = newState
        when (newState) {
            is PanelState.Empty -> {
                fillEmpty(newState)
                cards.show(this, EMPTY)
            }
            is PanelState.EntityDetails -> {
                content.removeAll()
                content.add(JBScrollPane(EntityDetailsView(project, newState)).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
                cards.show(this, DETAILS)
            }
            is PanelState.Details -> {
                content.removeAll()
                content.add(JBScrollPane(details(newState)).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
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
        box.add(JBLabel(AssetIcons.UNKNOWN, SwingConstants.CENTER).apply { horizontalAlignment = SwingConstants.CENTER })
        box.add(JBLabel(e.message, SwingConstants.CENTER).apply { horizontalAlignment = SwingConstants.CENTER })
        e.hint?.let { box.add(JBLabel("<html><center>${it}</center></html>", SwingConstants.CENTER).apply { foreground = secondary(); horizontalAlignment = SwingConstants.CENTER }) }
        empty.add(box, GridBagConstraints().apply { weightx = 1.0; weighty = 1.0; insets = JBUI.insets(24) })
    }

    private fun details(d: PanelState.Details): JComponent {
        val box = JPanel(VerticalLayout(0))
        box.add(header(d))
        box.add(columnHeader())
        val rows = JPanel(VerticalLayout(0)).apply { border = JBUI.Borders.empty(4, 0) }
        for (row in d.meta.rows) rows.add(rowOf(row))
        box.add(rows)
        d.faces?.let { box.add(previews(it)) }
        return JPanel(BorderLayout()).apply { add(box, BorderLayout.NORTH) }
    }

    private fun header(d: PanelState.Details): JComponent {
        val text = JPanel(VerticalLayout(JBUI.scale(2))).apply {
            add(JBLabel(d.name).apply { font = JBFont.label().asBold().biggerOn(1f) })
            add(JBLabel(AbyssusBundle.message("propertiesSubtitle", (d.meta.type ?: AbyssusBundle.message("propertiesUnknownType")).lowercase())).apply { foreground = secondary() })
        }
        return JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
            border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0), JBUI.Borders.empty(12, 16))
            add(JBLabel(AssetIcons.forType(d.meta.type)), BorderLayout.WEST)
            add(text, BorderLayout.CENTER)
        }
    }

    private fun columnHeader(): JComponent = twoColumns(
        JBLabel(AbyssusBundle.message("propertiesNameColumn").uppercase()).apply { foreground = secondary(); font = JBFont.small() },
        JBLabel(AbyssusBundle.message("propertiesValueColumn").uppercase()).apply { foreground = secondary(); font = JBFont.small() },
    ).apply { border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0), JBUI.Borders.empty(6, 16)) }

    private fun rowOf(row: PropertyRow): JComponent = when (row.kind) {
        RowKind.HEADING -> twoColumns(JBLabel(row.name).apply { foreground = ACCENT }, JBLabel("")).apply {
            border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0), JBUI.Borders.empty(10, 16, 4, 16))
        }
        else -> {
            val indent = if (row.kind == RowKind.ADDITIONAL) "    " else ""
            val value = JBLabel(row.value).apply { font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size); toolTipText = row.value.ifEmpty { null } }
            twoColumns(JBLabel(indent + row.name), value).apply { border = JBUI.Borders.empty(4, 16) }
        }
    }

    private fun twoColumns(name: JComponent, value: JComponent): JPanel = JPanel(GridBagLayout()).apply {
        add(name, GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.NORTHWEST; ipadx = 0 }.also { name.preferredSize = Dimension(JBUI.scale(NAME_COLUMN), name.preferredSize.height) })
        add(value, GridBagConstraints().apply { gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.NORTHWEST })
    }

    private fun previews(faces: List<FaceCell>): JComponent {
        val grid = JPanel(GridLayout(0, 3, JBUI.scale(10), JBUI.scale(10)))
        for (f in faces) grid.add(faceCell(f))
        return JPanel(VerticalLayout(JBUI.scale(8))).apply {
            border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0), JBUI.Borders.empty(12, 16, 16, 16))
            add(JBLabel(AbyssusBundle.message("propertiesFacePreviews").uppercase()).apply { foreground = secondary(); font = JBFont.small() })
            add(grid)
        }
    }

    private fun faceCell(f: FaceCell): JComponent {
        val caption = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            add(JBLabel(f.face), BorderLayout.WEST)
            add(JBLabel(f.file).apply { foreground = secondary(); font = Font(Font.MONOSPACED, Font.PLAIN, JBFont.small().size); horizontalAlignment = SwingConstants.RIGHT }, BorderLayout.CENTER)
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
