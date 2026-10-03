/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import com.intellij.ide.DataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.ecs.scene.ComponentEditor
import net.nevinsky.abyssus.ecs.scene.EditResult
import net.nevinsky.abyssus.ecs.scene.FieldKind
import net.nevinsky.abyssus.ecs.scene.FieldValue
import net.nevinsky.abyssus.filetype.ComponentIcons
import net.nevinsky.abyssus.filetype.PropertyIcons
import net.nevinsky.abyssus.projectView.SceneComponentEdits
import net.nevinsky.abyssus.projectView.addComponentGroup
import net.nevinsky.abyssus.projectView.reportRejection
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * An entity (or one component) of a scene with an editor for every field of the components the plugin models. Editors
 * are named `field-<Kind>-<field>` (their message `error-<Kind>-<field>`), a section's Remove button `remove-<Kind>` and
 * the Add button `add-component`, which is how tests reach them.
 */
internal class EntityDetailsView(private val project: Project, private val state: PanelState.EntityDetails) : JPanel(BorderLayout()) {
    private val target = state.target

    init {
        val box = JPanel(VerticalLayout(0))
        box.add(header())
        for (section in state.sections) box.add(sectionView(section))
        if (target.kind == null) box.add(addButton())
        add(box, BorderLayout.NORTH)
    }

    private fun secondary() = UIUtil.getContextHelpForeground()

    private fun header(): JComponent {
        val text = JPanel(VerticalLayout(JBUI.scale(2))).apply {
            add(JBLabel(state.name).apply { font = JBFont.label().asBold().biggerOn(1f); name = "entity-name" })
            add(JBLabel(AbyssusBundle.message("propertiesEntitySubtitle", target.entityId)).apply { foreground = secondary() })
        }
        return JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
            border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0), JBUI.Borders.empty(12, 16))
            add(JBLabel(PropertyIcons.ECS), BorderLayout.WEST)
            add(text, BorderLayout.CENTER)
        }
    }

    private fun sectionView(section: ComponentSection): JComponent {
        val title = JPanel(BorderLayout()).apply {
            add(JBLabel(section.label, ComponentIcons.forComponent(section.kind), JBLabel.LEADING).apply { font = JBFont.label().asBold() }, BorderLayout.WEST)
            if (section.raw == null) {
                add(JButton(AbyssusBundle.message("propertiesRemoveComponent")).apply {
                    name = "remove-${section.kind}"
                    addActionListener { reportRejection(project, SceneComponentEdits.remove(project, target.file, target.entityId, section.kind)) }
                }, BorderLayout.EAST)
            }
        }
        val body = JPanel(VerticalLayout(0)).apply { border = JBUI.Borders.empty(4, 0) }
        if (section.raw != null) {
            body.add(JBLabel(AbyssusBundle.message("propertiesUnmodeledNote")).apply { foreground = secondary(); border = JBUI.Borders.empty(2, 16) })
            body.add(JTextArea(section.raw).apply {
                isEditable = false
                name = "raw-${section.kind}"
                font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
                border = JBUI.Borders.empty(2, 16)
                isOpaque = false
            })
        } else {
            for (field in section.fields) body.add(fieldRow(section, field))
        }
        return JPanel(VerticalLayout(0)).apply {
            border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0), JBUI.Borders.empty(8, 16))
            add(title)
            add(body)
        }
    }

    private fun fieldRow(section: ComponentSection, field: FieldValue): JComponent {
        val error = JBLabel("").apply { foreground = JBColor.RED; name = "error-${section.kind}-${field.field}" }
        val editor = editorFor(section, field, error)
        editor.name = "field-${section.kind}-${field.field}"
        val isRange = section.kind == "LightComponent" && field.field == "range"
        val label = JBLabel(if (isRange) AbyssusBundle.message("lightRangeLabel") else field.field).apply {
            preferredSize = Dimension(JBUI.scale(LABEL_WIDTH), preferredSize.height)
        }
        if (isRange) editor.toolTipText = AbyssusBundle.message("lightRangeTooltip")
        return JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(2, 0)
            add(label, GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST })
            add(editor, GridBagConstraints().apply { gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
            add(error, GridBagConstraints().apply { gridx = 2; insets = JBUI.insetsLeft(8) })
        }
    }

    /** Saves [text] for [field]; a refused value goes back to what the file holds, with the reason beside the field. */
    private fun commit(section: ComponentSection, field: FieldValue, text: String, error: JBLabel, revert: () -> Unit) {
        if (text == field.value) return
        when (val result = SceneComponentEdits.update(project, target.file, target.entityId, section.kind, field.field, text)) {
            is EditResult.Rejected -> {
                error.text = result.reason
                revert()
            }
            EditResult.Unchanged -> {
                error.text = ""
                revert()
            }
            EditResult.Changed -> error.text = ""
        }
    }

    private fun editorFor(section: ComponentSection, field: FieldValue, error: JBLabel): JComponent {
        val useChoices = field.kind == FieldKind.CHOICE || (field.kind == FieldKind.ASSET_NAME && field.choices.size > 1)
        if (useChoices) {
            val choices = (if (field.optional) listOf("") else emptyList()) + field.choices
            val combo = ComboBox(choices.toTypedArray())
            combo.selectedItem = field.value
            combo.addActionListener {
                val chosen = combo.selectedItem as? String ?: return@addActionListener
                commit(section, field, chosen, error) { combo.selectedItem = field.value }
            }
            return combo
        }
        val textField = JBTextField(field.value)
        val save = { commit(section, field, textField.text, error) { textField.text = field.value } }
        textField.addActionListener { save() }
        textField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = save()
        })
        return textField
    }

    private fun addButton(): JComponent = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(12, 16)
        add(JButton(AbyssusBundle.message("propertiesAddComponent")).apply {
            name = "add-component"
            isEnabled = state.addable.isNotEmpty()
            toolTipText = if (state.addable.isEmpty()) AbyssusBundle.message("propertiesNothingToAdd") else null
            addActionListener {
                val group = addComponentGroup(project, target.file, target.entityId, state.addable)
                JBPopupFactory.getInstance()
                    .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(this), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
                    .showUnderneathOf(this)
            }
        }, BorderLayout.WEST)
    }

    private companion object {
        const val LABEL_WIDTH = 170
    }
}
