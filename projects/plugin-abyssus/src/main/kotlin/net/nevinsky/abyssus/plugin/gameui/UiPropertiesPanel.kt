/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import net.nevinsky.abyssus.lib.core.ui.model.UiActionType
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.*

/**
 * The properties of the selected widget, rendered from [UiPropertiesModel]: layout, content, style, bindings and the
 * click action. A value is applied as one document edit when the control commits (Enter, a choice, focus loss); text that
 * is not a valid value is flagged and nothing is written. Rows are rebuilt only when the field set really changed, so a
 * commit never takes focus away while someone types.
 */
class UiPropertiesPanel(private val controller: UiEditorController, private val model: UiPropertiesModel = UiPropertiesModel()) : JPanel(BorderLayout()) {
    private val form = JPanel(GridBagLayout())
    private var shown: Pair<String?, UiFieldGroups?> = null to null

    /** The control of each row by its field key, for tests and for focusing a row. */
    internal val components = LinkedHashMap<String, JComponent>()

    /** The warning label of each row that has one, by field key. */
    internal val warnings = LinkedHashMap<String, JBLabel>()

    private val none = ""

    init {
        add(JBScrollPane(form), BorderLayout.CENTER)
        controller.addListener(::refresh)
        refresh()
    }

    internal fun refresh() {
        val collection = controller.collection
        val id = controller.selection.widgetId
        val groups = if (collection != null && id != null) model.fieldsFor(collection, id) else null
        if (groups != null && shown.first == id && shown.second != null && shape(shown.second!!) == shape(groups)) {
            // same rows (an edit of this widget): update the values in place so the control being typed in stays
            shown = id to groups
            syncValues(groups)
            return
        }
        shown = id to groups
        components.clear(); warnings.clear()
        form.removeAll()
        var row = 0
        fun heading(key: String) {
            form.add(JBLabel("<html><b>${AbyssusBundle.message(key)}</b></html>"), constraints(row++, 0, 2))
        }
        fun add(field: UiField, widgetId: String) {
            val c = component(field, widgetId)
            c.name = field.key
            components[field.key] = c
            form.add(JBLabel(AbyssusBundle.message("uiField.${field.key}")), constraints(row, 0, 1))
            form.add(c, constraints(row++, 1, 1, fill = true))
            val warning = when (field) {
                is UiField.Binding -> field.warning
                is UiField.Action -> field.warning
                is UiField.Choice -> field.warning
                else -> null
            }
            if (warning != null) {
                val label = JBLabel(AbyssusBundle.message("uiWarning.${warning.name}")).apply { foreground = JBColor.RED; name = "warning-${field.key}" }
                warnings[field.key] = label
                form.add(label, constraints(row++, 1, 1))
            }
        }
        if (groups != null && id != null) {
            val widget = collection!!.let { c -> net.nevinsky.abyssus.lib.core.editor.ui.UiTree(c).widget(id) }
            form.add(JBLabel("${widget?.rawType.orEmpty()}  (${id})"), constraints(row++, 0, 2))
            for ((titleKey, fields) in listOf("uiGroupLayout" to groups.layout, "uiGroupContent" to groups.content, "uiGroupStyle" to groups.style,
                "uiGroupBindings" to groups.bindings, "uiGroupActions" to groups.actions)) {
                if (fields.isEmpty()) continue
                heading(titleKey)
                fields.forEach { add(it, id) }
            }
        }
        form.add(JPanel(), GridBagConstraints().apply { gridx = 0; gridy = row; weighty = 1.0; gridwidth = 2 })
        form.revalidate(); form.repaint()
    }

    /** What decides which controls exist: the rows, their options and warnings, not their current values. */
    private fun shape(g: UiFieldGroups): List<Any> = g.all.map { f ->
        when (f) {
            is UiField.Number -> listOf(f.key, "number")
            is UiField.Text -> listOf(f.key, "text")
            is UiField.Choice -> listOf(f.key, f.options, f.warning, f.value.takeIf { it != null && it !in f.options })
            is UiField.Binding -> listOf(f.key, f.options, f.warning, f.binding.takeIf { it != null && it !in f.options })
            is UiField.Action -> listOf(f.key, f.screens, f.warning, f.action?.type)
        }
    }

    private fun syncValues(g: UiFieldGroups) {
        for (f in g.all) {
            val c = components[f.key] ?: continue
            when {
                f is UiField.Number && c is JTextField -> setText(c, f.value?.let(::formatNumber).orEmpty())
                f is UiField.Text && c is JTextField -> setText(c, f.value)
                f is UiField.Choice && c is JComboBox<*> -> c.selectedItem = f.value ?: none
                f is UiField.Binding && c is JComboBox<*> -> c.selectedItem = f.binding ?: none
            }
        }
    }

    /** Never replaces what someone is typing: a focused field keeps its text. */
    private fun setText(field: JTextField, value: String) {
        field.putClientProperty(COMMITTED, value)
        if (!field.hasFocus() && field.text != value) field.text = value
    }

    private fun constraints(row: Int, column: Int, width: Int, fill: Boolean = false) = GridBagConstraints().apply {
        gridx = column; gridy = row; gridwidth = width; anchor = GridBagConstraints.WEST; insets = Insets(2, 4, 2, 4)
        if (fill) { this.fill = GridBagConstraints.HORIZONTAL; weightx = 1.0 }
    }

    private fun component(field: UiField, widgetId: String): JComponent = when (field) {
        is UiField.Number -> textRow(field.key, widgetId, field.value?.let(::formatNumber).orEmpty())
        is UiField.Text -> textRow(field.key, widgetId, field.value)
        is UiField.Choice -> choiceRow(field.key, widgetId, field.value, field.options)
        is UiField.Binding -> choiceRow(field.key, widgetId, field.binding, field.options)
        is UiField.Action -> actionRow(widgetId, field)
    }

    private fun formatNumber(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

    private fun textRow(key: String, widgetId: String, value: String): JTextField = JTextField(value, 12).also { field ->
        val normal = field.background
        // what the document holds now; kept current by [syncValues], so clearing a value just set is still a change
        field.putClientProperty(COMMITTED, value)
        fun commit() {
            if (field.text == field.getClientProperty(COMMITTED)) return
            val edit = model.edit(key, widgetId, field.text)
            if (edit == null) { field.background = ERROR; return }
            field.background = normal
            controller.edit(AbyssusBundle.message("uiCommandEditProperty"), edit)
        }
        field.addActionListener { commit() }
        field.addFocusListener(object : FocusAdapter() { override fun focusLost(e: FocusEvent) { commit() } })
    }

    private fun choiceRow(key: String, widgetId: String, value: String?, options: List<String>): JComboBox<String> {
        val items = (listOf(none) + options + listOfNotNull(value?.takeIf { it !in options })).distinct()
        return JComboBox(items.toTypedArray()).also { combo ->
            combo.selectedItem = value ?: none
            combo.addActionListener {
                val picked = combo.selectedItem as? String ?: none
                if (picked == (value ?: none)) return@addActionListener
                model.edit(key, widgetId, picked)?.let { controller.edit(AbyssusBundle.message("uiCommandEditProperty"), it) }
            }
        }
    }

    /** The click action: its type, then the screen to open or the action name to emit. */
    private fun actionRow(widgetId: String, field: UiField.Action): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        val types = listOf(none) + UiActionType.entries.map { it.json }
        val typeBox = JComboBox(types.toTypedArray()).apply { name = "action.type"; selectedItem = field.action?.type?.json ?: none }
        val targets = field.screens + listOfNotNull((field.action?.screenId ?: field.action?.name)?.takeIf { it !in field.screens })
        val targetBox = JComboBox((listOf(none) + targets).toTypedArray()).apply {
            name = "action.target"; isEditable = true; selectedItem = field.action?.screenId ?: field.action?.name ?: none
        }
        fun commit() {
            val type = UiActionType.entries.firstOrNull { it.json == typeBox.selectedItem }
            val target = (targetBox.selectedItem as? String)?.takeIf { it.isNotBlank() }
            controller.edit(AbyssusBundle.message("uiCommandEditAction"), model.actionEdit(widgetId, type, target, field.action?.assetName))
        }
        typeBox.addActionListener { if (typeBox.selectedItem != (field.action?.type?.json ?: none)) commit() }
        targetBox.addActionListener { if (targetBox.selectedItem != (field.action?.screenId ?: field.action?.name ?: none)) commit() }
        add(typeBox); add(targetBox)
    }

    private companion object {
        const val COMMITTED = "abyssus.committed"
        val ERROR: Color = Color(0xff, 0xcc, 0xcc)
    }
}
