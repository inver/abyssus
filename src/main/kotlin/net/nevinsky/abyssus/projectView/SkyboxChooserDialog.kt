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

package net.nevinsky.abyssus.projectView

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.filetype.AssetIcons
import java.awt.BorderLayout
import java.awt.Component
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import net.nevinsky.abyssus.properties.hdrThumbnail
import net.nevinsky.abyssus.properties.smallThumbnail
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

private const val THUMB_PIXELS = 56

/**
 * "Choose a skybox": the project's skyboxes after a None entry, a name filter, and Cancel / Assign. Nothing is written
 * here; after [showAndGet] returns true, [chosen] is the folder to assign (`null` for None).
 */
class SkyboxChooserDialog(project: Project, choices: List<SkyboxChoice>, current: String?) : DialogWrapper(project) {
    private val model = SkyboxPickerModel(choices, current)
    private val listModel = CollectionListModel(model.entries)
    private val list = JBList(listModel)
    private val found = JBLabel(model.foundText)
    private val footer = JBLabel(model.footerText)
    private val noMatch = JBLabel(AbyssusBundle.message("skyboxNoMatch")).apply {
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(8, 12)
    }
    private val filter = SearchTextField(false)

    /** The folder to write, `null` for None; meaningful once the dialog was closed with Assign. */
    val chosen: String? get() = model.selected

    init {
        title = AbyssusBundle.message("skyboxChooserTitle")
        setOKButtonText(AbyssusBundle.message("skyboxAssign"))
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = SkyboxCellRenderer()
        list.addListSelectionListener {
            if (it.valueIsAdjusting || list.selectedIndex < 0) return@addListSelectionListener
            model.selected = listModel.getElementAt(list.selectedIndex)?.name
            footer.text = model.footerText
        }
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                if (list.selectedIndex < 0) return false
                doOKAction()
                return true
            }
        }.installOn(list)
        filter.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = applyFilter()
        })
        filter.textEditor.emptyText.text = AbyssusBundle.message("skyboxFilterPlaceholder")
        list.fixedCellHeight = -1
        init()
        refreshList()
        loadThumbnails(choices)
    }

    /** Decodes the face images off the EDT and repaints the list when they are ready; rows without a folder keep empty cells. */
    private fun loadThumbnails(choices: List<SkyboxChoice>) {
        if (choices.none { it.folder != null }) return
        ApplicationManager.getApplication().executeOnPooledThread {
            for (choice in choices) {
                val dir = choice.folder ?: continue
                choice.thumbs = if (choice.hdr != null) listOf(choice.hdr.file?.let { hdrThumbnail(dir, it, THUMB_PIXELS) })
                else choice.faceFiles.map { name -> name?.let { smallThumbnail(dir, it, THUMB_PIXELS) } }
            }
            ApplicationManager.getApplication().invokeLater({ if (!isDisposed) list.repaint() }, ModalityState.any())
        }
    }

    private fun applyFilter() {
        model.filter = filter.text
        refreshList()
    }

    /** Rebuilds the rows from the model and highlights the selection when it is still listed. */
    private fun refreshList() {
        listModel.replaceAll(model.entries)
        found.text = model.foundText
        noMatch.isVisible = model.noMatch
        val row = model.entries.indexOfFirst { it?.name == model.selected }
        if (row >= 0) list.selectedIndex = row else list.clearSelection()
        footer.text = model.footerText
    }

    override fun createNorthPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
        val header = JPanel(BorderLayout()).apply {
            add(found.apply { foreground = UIUtil.getContextHelpForeground() }, BorderLayout.WEST)
        }
        val filterBox = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBLabel(AbyssusBundle.message("skyboxFilterLabel")).apply { labelFor = filter.textEditor }, BorderLayout.NORTH)
            add(filter, BorderLayout.CENTER)
        }
        add(header, BorderLayout.NORTH)
        add(filterBox, BorderLayout.CENTER)
        border = JBUI.Borders.emptyBottom(8)
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout()).apply {
        val body = JPanel(BorderLayout()).apply {
            add(list, BorderLayout.NORTH)
            add(noMatch, BorderLayout.CENTER)
            background = list.background
        }
        add(JBScrollPane(body), BorderLayout.CENTER)
        preferredSize = JBUI.size(460, 320)
    }

    override fun createSouthPanel(): JComponent = JPanel(BorderLayout()).apply {
        add(footer.apply { foreground = UIUtil.getContextHelpForeground() }, BorderLayout.WEST)
        add(super.createSouthPanel(), BorderLayout.EAST)
    }

    override fun getPreferredFocusedComponent(): JComponent = filter.textEditor

    /** Visible for tests: the rows as the list shows them. */
    internal val rows: List<SkyboxChoice?> get() = listModel.items

    internal val foundLabel: String get() = found.text

    internal val footerLabel: String get() = footer.text

    internal val noMatchShown: Boolean get() = noMatch.isVisible

    internal fun typeFilter(text: String) {
        filter.text = text
        applyFilter()
    }

    /** The component the list draws for row [index], for tests. */
    internal fun renderedRow(index: Int, selected: Boolean): JComponent =
        list.cellRenderer.getListCellRendererComponent(list, listModel.getElementAt(index), index, selected, selected) as JComponent

    internal fun clickRow(index: Int) {
        list.selectedIndex = index
    }
}

/**
 * The six face thumbnails of a row, or for an HDR sky ([panorama]) one 2:1 cell; a face or image still loading or
 * unreadable is an empty bordered cell.
 */
private class ThumbStrip : JComponent() {
    var images: List<BufferedImage?> = emptyList()
    var panorama = false

    override fun getPreferredSize() = Dimension(FACES * JBUI.scale(CELL) + (FACES - 1) * JBUI.scale(GAP), JBUI.scale(CELL))

    override fun getMinimumSize() = preferredSize

    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val cell = JBUI.scale(CELL)
            val arc = JBUI.scale(4)
            if (panorama) {
                val w = cell * 2
                images.firstOrNull()?.let { image ->
                    val clip = g2.clip
                    g2.clip(RoundRectangle2D.Float(0f, 0f, w.toFloat(), cell.toFloat(), arc.toFloat(), arc.toFloat()))
                    g2.drawImage(image, 0, 0, w, cell, null)
                    g2.clip = clip
                }
                g2.color = JBColor.border()
                g2.drawRoundRect(0, 0, w - 1, cell - 1, arc, arc)
                return
            }
            for (i in 0 until FACES) {
                val x = i * (cell + JBUI.scale(GAP))
                val image = images.getOrNull(i)
                if (image != null) {
                    val clip = g2.clip
                    g2.clip(RoundRectangle2D.Float(x.toFloat(), 0f, cell.toFloat(), cell.toFloat(), arc.toFloat(), arc.toFloat()))
                    g2.drawImage(image, x, 0, cell, cell, null)
                    g2.clip = clip
                }
                g2.color = JBColor.border()
                g2.drawRoundRect(x, 0, cell - 1, cell - 1, arc, arc)
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val FACES = 6
        const val CELL = 28
        const val GAP = 3
    }
}

/** A list row with the design's highlight: a rounded block inset from the list edges. */
private class SkyboxRow : JPanel(BorderLayout(JBUI.scale(12), 0)) {
    var selected = false

    init {
        isOpaque = false
        border = JBUI.Borders.empty(0, 18)
    }

    override fun getPreferredSize() = super.getPreferredSize().also { it.height = maxOf(it.height, JBUI.scale(ROW_HEIGHT)) }

    override fun paintComponent(g: Graphics) {
        if (selected) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = DesignColors.SELECTION
                val inset = JBUI.scale(6)
                g2.fillRoundRect(inset, 0, width - 2 * inset, height, JBUI.scale(12), JBUI.scale(12))
            } finally {
                g2.dispose()
            }
        }
        super.paintComponent(g)
    }

    private companion object {
        const val ROW_HEIGHT = 56
    }
}

/** One row: icon, name over a detail line, the face thumbnails, and on the right the unused badge or the scene count. */
private class SkyboxCellRenderer : ListCellRenderer<SkyboxChoice?> {
    private val iconLabel = JBLabel()
    private val nameLabel = JBLabel()
    private val detailLabel = JBLabel().apply { font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size - 1) }
    private val strip = ThumbStrip()
    private val badge = JBLabel()
    private val texts = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        add(nameLabel)
        add(detailLabel)
    }
    private val right = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0)).apply {
        isOpaque = false
        add(strip)
        add(badge)
    }
    private val row = SkyboxRow().apply {
        add(iconLabel, BorderLayout.WEST)
        add(texts, BorderLayout.CENTER)
        add(right, BorderLayout.EAST)
    }

    override fun getListCellRendererComponent(
        list: JList<out SkyboxChoice?>,
        value: SkyboxChoice?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val fg = UIUtil.getListForeground(false, true)
        row.selected = isSelected
        nameLabel.foreground = fg
        detailLabel.foreground = UIUtil.getContextHelpForeground()
        nameLabel.font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        if (value == null) {
            iconLabel.icon = AllIcons.General.Remove
            nameLabel.text = AbyssusBundle.message("skyboxNone")
            detailLabel.text = AbyssusBundle.message("skyboxNoneDetail")
            strip.isVisible = false
            badge.text = ""
            badge.border = null
        } else {
            iconLabel.icon = AssetIcons.forType(value.type)
            nameLabel.text = value.name
            detailLabel.text = value.detail
            strip.images = value.thumbs
            strip.panorama = value.hdr != null
            // an HDR sky always shows its one cell: a placeholder when its image cannot be read
            strip.isVisible = value.faceFiles.isNotEmpty() || value.hdr != null
            if (value.unused) {
                badge.text = AbyssusBundle.message("skyboxUnused")
                badge.foreground = UNUSED_COLOR
                badge.border = JBUI.Borders.compound(RoundedLineBorder(UNUSED_COLOR, JBUI.scale(6)), JBUI.Borders.empty(0, 6))
            } else {
                badge.text = AbyssusBundle.message("skyboxUsedBy", value.sceneCount)
                badge.foreground = UIUtil.getContextHelpForeground()
                badge.border = null
            }
        }
        return row
    }

    private companion object {
        val UNUSED_COLOR = JBColor(0xA8741A, 0xE0A458)
    }
}
