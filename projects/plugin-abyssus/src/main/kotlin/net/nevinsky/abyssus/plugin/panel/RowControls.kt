/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.panel

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.*
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.panel.*
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.filetype.TreeActionIcons
import net.nevinsky.abyssus.plugin.projectView.DesignColors
import net.nevinsky.abyssus.plugin.ui.CommitOutcome
import net.nevinsky.abyssus.plugin.ui.checkEditor
import net.nevinsky.abyssus.plugin.ui.choiceEditor
import net.nevinsky.abyssus.plugin.ui.textEditor
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.*

/** What the rows of a section need from the view that hosts them: saving a field, the hosted editors and links. */
interface RowHooks {
    /** Saves [text] for [ref]; the editor shows a refusal and puts the stored value back. */
    fun commit(ref: FieldRef, text: String): CommitOutcome

    /** The existing typed editor hosted under [key] (terrain, foliage, faces, HDR, antialiasing, show FPS), or null. */
    fun custom(key: String): JComponent?

    /** Opens the Screen Editor on the screen or asset the panel shows. */
    fun openScreenEditor()

    /** A finished row for the asset property [key] (its typed editor and message), or null to use the plain editor. */
    fun assetField(key: String, label: String): JComponent? = null
}

/** The component name of the editor of [ref]: `field-<Kind>-<field>`, which tests and the page objects look up. */
fun fieldName(ref: FieldRef): String = if (ref.kind == "asset") {
    "asset-field-${ref.field}"
} else "field-${ref.kind}-${ref.field}"

/** The name of the message beside it: `error-<Kind>-<field>`. */
fun errorName(ref: FieldRef): String =
    if (ref.kind == "asset") "asset-error-${ref.field}" else "error-${ref.kind}-${ref.field}"

/**
 * One row of a section: a 150 px label column, the control at the look and feel's field height, and the reason a value was refused beside it. A row
 * that spans the width (a note, a sub-heading, a table) has no label.
 */
fun fieldRow(label: String, control: JComponent, error: JComponent? = null, labelName: String? = null): JComponent =
    JPanel(GridBagLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.empty(1, 0)
        val labelView = JBLabel(label).apply {
            labelName?.let { name = it }
            preferredSize = Dimension(PanelMetrics.scaled(PanelMetrics.LABEL_COLUMN), preferredSize.height)
        }
        add(labelView, GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST })
        add(
            control,
            GridBagConstraints().apply {
                gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
            })
        if (error != null) add(error, GridBagConstraints().apply { gridx = 2; insets = JBUI.insetsLeft(8) })
    }

/** The row of [row] for a section whose rows are hosted by [hooks]. [tooltip] help goes on the control. */
fun rowComponent(row: Row, hooks: RowHooks): JComponent = (row as? TextRow)?.field?.takeIf { it.kind in HOSTED }
    ?.let { hooks.assetField(it.field, row.label) } ?: (row as? AssetRow)?.field?.takeIf { it.kind in HOSTED }
    ?.let { hooks.assetField(it.field, row.label) } ?: when (row) {
    is TextRow -> editable(row.label, row.field, row.dim, row.tooltip, hooks) { ref, error ->
        textEditor(row.value, error) { hooks.commit(ref, it) }.also { it.styled(row.dim) }
    } ?: fieldRow(row.label, readonlyValue(row.value, row.dim))

    is NumberRow -> editable(row.label, row.field, row.dim, row.tooltip, hooks) { ref, error ->
        val field = textEditor(row.value, error) { hooks.commit(ref, it) }.also { it.styled(row.dim) }
        field.pinFieldHeight(PanelMetrics.scaled(150))
        if (row.unit.isEmpty()) field else unitBox(field, row.unit)
    } ?: fieldRow(row.label, readonlyValue(row.value + unitSuffix(row.unit), row.dim))

    is VectorRow -> vectorRow(row.label, row.axes, row.dim, hooks)
    is ChoiceRow -> editable(row.label, row.field, row.dim, null, hooks) { ref, error ->
        val choices = (if (row.optional) listOf("") else emptyList()) + row.choices
        choiceEditor(choices.ifEmpty { listOf(row.value) }, row.value, error) { hooks.commit(ref, it) }
            .also { it.pinFieldHeight(PanelMetrics.scaled(220)) }
    } ?: fieldRow(row.label, readonlyValue(row.value, row.dim))

    is CheckRow -> editable(row.label, row.field, row.dim, null, hooks) { ref, error ->
        checkEditor(row.checked, error) { hooks.commit(ref, it.toString()) }.also { it.text = row.text }
    } ?: fieldRow(row.label, JBCheckBox(row.text, row.checked).apply { isEnabled = false; name = "check-${row.label}" })

    is ColorRow -> colorRow(row, hooks)
    is AssetRow -> assetRow(row, hooks)
    is ReadonlyRow ->
        if (row.label.isEmpty()) JBTextArea(row.value).apply {
            isEditable = false; font = monoFont(); isOpaque = false; border = JBUI.Borders.empty(2, 0)
            name = "raw"
        } else fieldRow(row.label, readonlyValue(row.value, row.dim), labelName = "ro-label-${row.label}")

    is LinkRow -> JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false; border = JBUI.Borders.empty(6, 0)
        add(ActionLink(row.text) { hooks.openScreenEditor() }.apply { name = "link-screen-editor" })
    }

    is NoteRow -> JBLabel("<html>${row.text}</html>").apply {
        name = "note"; foreground = DesignColors.SECONDARY; font = JBFont.small(); border = JBUI.Borders.empty(2, 0)
    }

    is SubHeadingRow -> JBLabel(row.text).apply {
        name = "group-${row.text}"; foreground = DesignColors.SECONDARY; font = JBFont.small().asBold()
        border = JBUI.Borders.empty(8, 0, 2, 0)
    }

    is TableRow -> tableView(row)
    is CustomRow -> hooks.custom(row.key) ?: JPanel().apply { isVisible = false }
}

/** The kinds of [FieldRef] whose finished row the host supplies: an asset property, a scene's skybox. */
private val HOSTED = setOf("asset", "scene")

/** Wide enough for a full float such as `-0.94851714` in the monospace font. */
private const val AXIS_BOX = 108

private fun unitSuffix(unit: String) = if (unit.isEmpty()) "" else " $unit"

private fun JBTextField.styled(dim: Boolean) {
    font = monoFont()
    if (dim) foreground = DesignColors.SECONDARY
}

private fun readonlyValue(text: String, dim: Boolean): JComponent = JBLabel(text).apply {
    font = monoFont(); toolTipText = text.ifEmpty { null }
    if (dim) foreground = DesignColors.SECONDARY
}

/** [field] followed by its unit (`kg`, `m`, `N`, `s`, `m²`, `°`). */
private fun unitBox(field: JComponent, unit: String): JComponent = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
    isOpaque = false
    add(field, BorderLayout.WEST)
    add(JBLabel(unit).apply { name = "unit"; foreground = DesignColors.SECONDARY }, BorderLayout.CENTER)
}

/** A labelled row whose editor [build] makes for [ref], named for tests; null when the row is not editable. */
private fun editable(
    label: String, ref: FieldRef?, dim: Boolean, tooltip: String?, hooks: RowHooks,
    build: (FieldRef, JBLabel) -> JComponent,
): JComponent? {
    ref ?: return null
    val error = JBLabel("").apply { foreground = Color.RED; name = errorName(ref) }
    val editor = build(ref, error)
    // every text box and combo has the same height
    if (editor is JBTextField || editor is ComboBox<*>)
        editor.pinFieldHeight()
    nameEditor(editor, ref)
    tooltip?.let { editor.toolTipText = it }
    return fieldRow(label, editor, error, labelName = "label-${ref.kind}-${ref.field}")
}

/** Names the editor of [ref] and, for a wrapper (a value with a unit), the text field inside it. */
private fun nameEditor(editor: JComponent, ref: FieldRef) {
    editor.name = fieldName(ref)
    if (editor is JPanel) editor.components.firstOrNull()?.let { (it as? JComponent)?.name = fieldName(ref) }
}

private fun vectorRow(label: String, axes: List<Axis>, dim: Boolean, hooks: RowHooks): JComponent {
    val error = JBLabel("").apply { foreground = Color.RED }
    val boxes = JPanel(GridLayout(1, axes.size.coerceAtLeast(1), JBUI.scale(8), 0)).apply {
        isOpaque = false
        for (axis in axes) {
            val ref = axis.field
            val field = if (ref == null) JBTextField(axis.value).apply { isEditable = false }
            else textEditor(axis.value, error) { hooks.commit(ref, it) }.also { it.name = fieldName(ref) }
            field.font = monoFont()
            if (dim) field.foreground = DesignColors.SECONDARY
            val tag = JBLabel(axis.name).apply { foreground = DesignColors.SECONDARY; name = "axis-label-${axis.name}" }
            add(axisBox(tag, field))
        }
    }
    axes.firstOrNull { it.field != null }?.field?.let { error.name = errorName(it) }
    return fieldRow(label, boxes, error, labelName = "label-vector-$label")
}

/** An outlined rounded box with the axis [tag] at the left and [field]'s value right-aligned; the outline follows focus. */
private fun axisBox(tag: JBLabel, field: JBTextField): JComponent {
    field.horizontalAlignment = SwingConstants.RIGHT
    field.border = JBUI.Borders.empty()
    field.isOpaque = false
    val box = object : JPanel(BorderLayout(JBUI.scale(6), 0)) {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(8)
                g2.color = field.background
                // Darcula text fields draw their outline this far inside their bounds; match it so the edges line up
                val inset = DarculaUIUtil.BW.get()
                val w = width - 2 * inset - 1
                val h = height - 2 * inset - 1
                g2.fillRoundRect(inset, inset, w, h, arc, arc)
                g2.color = if (field.hasFocus()) UIUtil.getFocusedBorderColor() else DesignColors.BORDER
                g2.drawRoundRect(inset, inset, w, h, arc, arc)
            } finally {
                g2.dispose()
            }
        }
    }.apply {
        isOpaque = false
        border = JBUI.Borders.empty(0, 10)
        add(tag, BorderLayout.WEST)
        add(field, BorderLayout.CENTER)
    }
    field.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) = box.repaint()
        override fun focusLost(e: FocusEvent) = box.repaint()
    })
    // the height of every other text box, from a plain field in the same look and feel
    val height = JBTextField().apply { font = monoFont(); pinFieldHeight() }.preferredSize.height
    box.preferredSize = Dimension(PanelMetrics.scaled(AXIS_BOX), height)
    box.minimumSize = Dimension(0, height)
    box.maximumSize = Dimension(Int.MAX_VALUE, height)
    return box
}

private fun colorRow(row: ColorRow, hooks: RowHooks): JComponent {
    val swatch = JPanel().apply {
        name = "swatch"; preferredSize = Dimension(JBUI.scale(16), JBUI.scale(16))
        background = runCatchingKeepingCancellation { Color.decode(row.hex) }.getOrDefault(Color.GRAY)
        border = BorderFactory.createLineBorder(DesignColors.BORDER)
    }
    val line = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
        isOpaque = false
        add(swatch)
        add(readonlyValue(row.hex, row.dim).also { it.name = "hex" })
    }
    if (row.axes.isEmpty()) {
        return fieldRow(row.label, line)
    }
    val channels = vectorRow("", row.axes, row.dim, hooks)
    val both = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
        add(line); add(channels)
    }
    return fieldRow(row.label, both, labelName = "label-color")
}

private fun assetRow(row: AssetRow, hooks: RowHooks): JComponent {
    val ref = row.field
    val icon = JBLabel(panelIcon(row.iconKey))
    if (ref == null) {
        val field = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            isOpaque = false
            add(icon)
            add(readonlyValue(row.name, row.dim))
        }
        return fieldRow(row.label, field)
    }
    if (row.choices.size <= 1) {
        // nothing to choose from: the name is typed
        val typedError = JBLabel("").apply { foreground = Color.RED; name = errorName(ref) }
        val typed = textEditor(row.name, typedError) { hooks.commit(ref, it) }.also {
            it.name = fieldName(ref); it.font = monoFont()
            it.pinFieldHeight(PanelMetrics.scaled(300))
        }
        val line = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            isOpaque = false;
            add(icon);
            add(typed)
        }
        return fieldRow(row.label, line, typedError, labelName = "label-${ref.kind}-${ref.field}")
    }
    val error = JBLabel("").apply { foreground = Color.RED; name = errorName(ref) }
    val choices = (listOf("") + row.choices + row.name).distinct().ifEmpty { listOf(row.name) }
    val combo: ComboBox<String> = choiceEditor(choices, row.name, error) { hooks.commit(ref, it) }
    combo.name = fieldName(ref)
    combo.pinFieldHeight(PanelMetrics.scaled(300))
    val line = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
        isOpaque = false
        add(icon)
        add(combo)
        if (row.browse) add(JButton(TreeActionIcons.BROWSE).apply {
            name = "browse-${ref.kind}-${ref.field}"
            toolTipText = AbyssusBundle.message("panelBrowse")
            accessibleContext.accessibleName = AbyssusBundle.message("panelBrowse")
            preferredSize =
                Dimension(PanelMetrics.scaled(PanelMetrics.BROWSE), PanelMetrics.scaled(PanelMetrics.BROWSE))
            addActionListener { combo.showPopup() }
        })
    }
    return fieldRow(row.label, line, error, labelName = "label-${ref.kind}-${ref.field}")
}

private fun tableView(row: TableRow): JComponent {
    val columns = row.columns.size
    val box = JPanel().apply {
        name = "table"; layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
        border = BorderFactory.createLineBorder(DesignColors.HAIRLINE)
    }
    val rows = listOf(row.columns to true) + row.rows.map { it to false }
    for ((cells, header) in rows) {
        box.add(JPanel(GridLayout(1, columns, JBUI.scale(8), 0)).apply {
            isOpaque = false; name = if (header) "table-header" else "table-row"
            border = JBUI.Borders.empty(4, 8)
            for (cell in cells.take(columns)) add(JBLabel(cell).apply {
                if (header) {
                    font = JBFont.small(); foreground = DesignColors.SECONDARY
                } else font = monoFont()
                toolTipText = cell.ifEmpty { null }
            })
        })
    }
    return JPanel(BorderLayout()).apply {
        isOpaque = false;
        border = JBUI.Borders.empty(4, 0);
        add(
            box,
            BorderLayout.CENTER
        )
    }
}
