/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import net.nevinsky.abyssus.EditorBundle
import com.intellij.ide.DataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.ecs.scene.EditResult
import net.nevinsky.abyssus.ecs.scene.FieldKind
import net.nevinsky.abyssus.ecs.scene.FieldValue
import net.nevinsky.abyssus.filetype.ComponentIcons
import net.nevinsky.abyssus.filetype.PropertyIcons
import net.nevinsky.abyssus.projectView.SceneComponentEdits
import net.nevinsky.abyssus.projectView.addComponentGroup
import net.nevinsky.abyssus.projectView.reportRejection
import net.nevinsky.abyssus.editor.ray.RayDataEdit
import net.nevinsky.abyssus.editor.ray.RayDataError
import net.nevinsky.abyssus.editor.ray.RayOpticalField
import net.nevinsky.abyssus.filetype.SceneRayEdits
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
import net.nevinsky.abyssus.dto.MetaFiles

/**
 * An entity (or one component) of a scene with an editor for every field of the components the plugin models. Editors
 * are named `field-<Kind>-<field>` (their message `error-<Kind>-<field>`), a section's Remove button `remove-<Kind>` and
 * the Add button `add-component`, which is how tests reach them. A model's Render section also lists its materials'
 * Ray Tracing overrides: `optics-<field>-<material>` (message `optics-error-<material>`), `optics-unresolved-<material>`
 * and `optics-problem`.
 */
internal class EntityDetailsView(
    private val project: Project,
    private val state: PanelState.EntityDetails,
    private val metaFiles: MetaFiles
) : JPanel(BorderLayout()) {
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
            add(JBLabel(AbyssusBundle.message("propertiesEntitySubtitle", target.entityId)).apply {
                foreground = secondary()
            })
        }
        return JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(12, 16)
            )
            add(JBLabel(PropertyIcons.ECS), BorderLayout.WEST)
            add(text, BorderLayout.CENTER)
        }
    }

    private fun sectionView(section: ComponentSection): JComponent {
        val title = JPanel(BorderLayout()).apply {
            add(JBLabel(section.label, ComponentIcons.forComponent(section.kind), JBLabel.LEADING).apply {
                font = JBFont.label().asBold()
            }, BorderLayout.WEST)
            if (section.raw == null) {
                add(JButton(AbyssusBundle.message("propertiesRemoveComponent")).apply {
                    name = "remove-${section.kind}"
                    addActionListener {
                        reportRejection(
                            project,
                            SceneComponentEdits.remove(project, target.file, target.entityId, section.kind)
                        )
                    }
                }, BorderLayout.EAST)
            }
        }
        val body = JPanel(VerticalLayout(0)).apply { border = JBUI.Borders.empty(4, 0) }
        if (section.raw != null) {
            body.add(JBLabel(AbyssusBundle.message("propertiesUnmodeledNote")).apply {
                foreground = secondary(); border = JBUI.Borders.empty(2, 16)
            })
            body.add(JTextArea(section.raw).apply {
                isEditable = false
                name = "raw-${section.kind}"
                font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
                border = JBUI.Borders.empty(2, 16)
                isOpaque = false
            })
        } else {
            var group = ""
            for (field in section.fields) {
                if (field.group != group) {
                    group = field.group
                    if (group.isNotEmpty()) body.add(JBLabel(group).apply {
                        name = "group-${section.kind}-$group"
                        foreground = secondary()
                        font = JBFont.small().asBold()
                        border = JBUI.Borders.empty(6, 0, 2, 0)
                    })
                }
                body.add(fieldRow(section, field))
            }
        }
        if (section.kind == "RenderComponent") state.optics?.let { body.add(opticsView(it)) }
        return JPanel(VerticalLayout(0)).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(8, 16)
            )
            add(title)
            add(body)
        }
    }

    /** Transmission (in percent) and IOR per PBR material of the model, as overrides of this entity only. */
    private fun opticsView(optics: RenderOptics): JComponent = JPanel(VerticalLayout(JBUI.scale(2))).apply {
        add(JBLabel(AbyssusBundle.message("propertiesOptics")).apply {
            foreground = secondary(); font = JBFont.small().asBold(); border = JBUI.Borders.empty(8, 0, 2, 0)
        })
        add(JBLabel("<html>${AbyssusBundle.message("propertiesOpticsNote")} ${AbyssusBundle.message("propertiesOpticsRange")}</html>").apply {
            foreground = secondary(); font = JBFont.small(); name = "optics-note"
        })
        optics.problem?.let {
            add(JBLabel("<html>$it</html>").apply {
                foreground = JBColor.RED; name = "optics-problem"
            })
        }
        for (row in optics.materials) add(opticsRow(optics, row))
        for (id in optics.unresolved) add(
            JBLabel(
                "<html>${
                    AbyssusBundle.message(
                        "propertiesOpticsUnresolved",
                        id
                    )
                }</html>"
            ).apply {
                foreground = JBColor.RED; name = "optics-unresolved-$id"
            })
    }

    private fun opticsRow(optics: RenderOptics, row: OpticalMaterialRow): JComponent {
        val box = JPanel(VerticalLayout(JBUI.scale(2))).apply { border = JBUI.Borders.emptyTop(4) }
        box.add(JBLabel(row.id ?: AbyssusBundle.message("propertiesOpticsUnnamed")).apply {
            font = JBFont.label().asBold()
        })
        val id = row.id
        if (id == null || row.error != null) {
            box.add(JBLabel("<html>${EditorBundle.message("propertiesRayError${(row.error ?: RayDataError.MATERIAL_ID).name}")}</html>").apply {
                foreground = secondary(); name = "optics-error-${id ?: ""}"
            })
            return box
        }
        val error =
            JBLabel(row.errors.values.firstOrNull()?.let { EditorBundle.message("propertiesRayError${it.name}") }
                ?: "").apply {
                foreground = JBColor.RED; name = "optics-error-$id"
            }
        for (field in RayOpticalField.entries) {
            val stored = row.stored[field]
            val shown = stored?.takeIf { it.isNumber }?.doubleValue() ?: field.default
            val text =
                if (field == RayOpticalField.TRANSMISSION) percent(shown) else stored?.takeIf { it.isNumber }?.asText()
                    ?: number(shown)
            val editor = JBTextField(text).apply { name = "optics-${field.key}-$id"; columns = 6 }
            val save = {
                if (editor.text.trim() != text) {
                    // Transmission is typed in percent; the scene stores the 0..1 fraction
                    val value = if (field == RayOpticalField.TRANSMISSION) editor.text.trim().toDoubleOrNull()
                        ?.takeIf { it.isFinite() }?.let { number(it / 100) } else editor.text
                    val result = if (value == null) RayDataEdit.Rejected(RayDataError.NUMBER)
                    else SceneRayEdits.material(
                        project,
                        target.file,
                        target.entityId,
                        id,
                        field,
                        stored,
                        value,
                        optics.identities
                    )
                    when (result) {
                        RayDataEdit.Changed -> error.text = ""
                        RayDataEdit.Unchanged -> {
                            error.text = ""; editor.text = text
                        }

                        RayDataEdit.Conflict -> {
                            error.text = AbyssusBundle.message("propertiesRayConflict"); editor.text = text
                        }

                        is RayDataEdit.Rejected -> {
                            error.text = EditorBundle.message("propertiesRayError${result.error.name}"); editor.text =
                                text
                        }
                    }
                }
            }
            editor.addActionListener { save() }
            editor.addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) = save()
            })
            val label =
                JBLabel(AbyssusBundle.message(if (field == RayOpticalField.TRANSMISSION) "propertiesOpticsTransmission" else "propertiesOpticsIor")).apply {
                    preferredSize = Dimension(JBUI.scale(LABEL_WIDTH), preferredSize.height)
                }
            box.add(JPanel(GridBagLayout()).apply {
                add(label, GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST })
                add(
                    editor,
                    GridBagConstraints().apply { gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
            })
        }
        box.add(error)
        return box
    }

    /** [fraction] as a percentage without float noise (0.07 shows as 7, not 7.000000000000001). */
    private fun percent(fraction: Double) = number(fraction * 100)

    private fun number(value: Double): String =
        java.math.BigDecimal(value).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()

    private fun fieldRow(section: ComponentSection, field: FieldValue): JComponent {
        val error = JBLabel("").apply { foreground = JBColor.RED; name = "error-${section.kind}-${field.field}" }
        val editor = editorFor(section, field, error)
        editor.name = "field-${section.kind}-${field.field}"
        val labelKey = if (section.kind == "LightComponent") when (field.field) {
            "range" -> "lightRangeLabel"
            "coneAngle" -> "lightConeAngleLabel"
            "edgeSoftness" -> "lightEdgeSoftnessLabel"
            else -> null
        } else null
        val label = JBLabel(labelKey?.let { AbyssusBundle.message(it) } ?: field.label).apply {
            name = "label-${section.kind}-${field.field}"
            preferredSize = Dimension(JBUI.scale(LABEL_WIDTH), preferredSize.height)
        }
        if (section.kind == "LightComponent" && field.field == "range") editor.toolTipText =
            AbyssusBundle.message("lightRangeTooltip")
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
        when (val result = SceneComponentEdits.update(
            project,
            target.file,
            target.entityId,
            section.kind,
            field.field,
            text,
            metaFiles
        )) {
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
        if (field.kind == FieldKind.BOOLEAN) {
            val box = JBCheckBox("", field.value == "true")
            box.addActionListener {
                commit(section, field, box.isSelected.toString(), error) {
                    box.isSelected = field.value == "true"
                }
            }
            return box
        }
        val useChoices =
            field.kind == FieldKind.CHOICE || (field.kind == FieldKind.ASSET_NAME && field.choices.size > 1)
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
                val group = addComponentGroup(project, target.file, target.entityId, state.addable, metaFiles)
                JBPopupFactory.getInstance()
                    .createActionGroupPopup(
                        null,
                        group,
                        DataManager.getInstance().getDataContext(this),
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                        true
                    )
                    .showUnderneathOf(this)
            }
        }, BorderLayout.WEST)
    }

    private companion object {
        const val LABEL_WIDTH = 170
    }
}
