/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.lib.core.editor.water.InvalidWater
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdit
import net.nevinsky.abyssus.lib.core.editor.water.WaterInput
import net.nevinsky.abyssus.lib.core.editor.water.WaterText
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.ui.CommitOutcome
import net.nevinsky.abyssus.plugin.ui.checkEditor
import net.nevinsky.abyssus.plugin.ui.textEditor
import net.nevinsky.abyssus.plugin.water.WaterSceneEdits
import net.nevinsky.abyssus.plugin.water.waterErrorText
import net.nevinsky.abyssus.plugin.water.waterFieldLabel
import net.nevinsky.abyssus.plugin.water.waterIssueText
import net.nevinsky.abyssus.plugin.water.waterPresetLabel
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The editors of one water surface. The name is `water-name` (message `water-name-error`), the visibility box
 * `water-enabled`, and each value `water-field-<FIELD>` with its message `water-error-<FIELD>`; tests reach them by
 * those names. A confirmed valid edit is one undoable scene command; an invalid one shows its reason and puts the stored
 * value back.
 */
internal class WaterPropertiesView(private val project: Project, private val state: PanelState.WaterDetails) : JPanel(BorderLayout()) {
    private val edits = WaterSceneEdits(project)
    private val input = WaterText()
    private val target = state.target

    init {
        val box = JPanel(VerticalLayout(0))
        box.add(header())
        if (state.surface is InvalidWater) {
            box.add(JBLabel("<html>${AbyssusBundle.message("waterInvalidNote")}</html>").apply {
                name = "water-invalid-note"; foreground = JBColor.RED; border = JBUI.Borders.empty(8, 16)
            })
        }
        box.add(section("waterSectionPosition", WaterField.X, WaterField.LEVEL, WaterField.Z))
        box.add(section("waterSectionSize", WaterField.WIDTH, WaterField.LENGTH))
        box.add(section("waterSectionAppearance", WaterField.TINT_R, WaterField.TINT_G, WaterField.TINT_B, WaterField.CLARITY))
        box.add(section("waterSectionWaves", WaterField.WAVE_AMPLITUDE, WaterField.WAVE_LENGTH, WaterField.WAVE_SPEED))
        box.add(section("waterSectionFoam", WaterField.FOAM_AMOUNT, WaterField.FOAM_WIDTH))
        add(box, BorderLayout.NORTH)
    }

    private fun secondary() = UIUtil.getContextHelpForeground()

    private fun header(): JComponent {
        val nameError = JBLabel("").apply { foreground = JBColor.RED; name = "water-name-error" }
        val nameField = textEditor(state.surface.name, nameError) { text ->
            outcome(edits.rename(target.file, target.id, text))
        }.apply { name = "water-name" }
        val enabled = state.enabled
        val rows = JPanel(VerticalLayout(JBUI.scale(2))).apply {
            add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                isOpaque = false
                add(JBLabel(AbyssusBundle.message("waterNameLabel")), BorderLayout.WEST)
                add(nameField, BorderLayout.CENTER)
                add(nameError, BorderLayout.EAST)
            })
            add(JBLabel(state.preset?.let { AbyssusBundle.message("waterPropertiesSubtitle", waterPresetLabel(it)) }
                ?: AbyssusBundle.message("waterPropertiesSubtitleUnknown")).apply { foreground = secondary(); name = "water-subtitle" })
            add(JBLabel(AbyssusBundle.message("waterAbyssusOnlyNote")).apply { foreground = secondary(); font = JBFont.small(); name = "water-abyssus-only" })
            if (enabled != null) add(visibilityRow(enabled))
        }
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0), JBUI.Borders.empty(12, 16))
            add(rows, BorderLayout.CENTER)
        }
    }

    private fun visibilityRow(enabled: Boolean): JComponent {
        val error = JBLabel("").apply { foreground = JBColor.RED; name = "water-enabled-error" }
        val box: JBCheckBox = checkEditor(enabled, error) { outcome(edits.setEnabled(target.file, target.id, it)) }
        box.name = "water-enabled"
        box.text = AbyssusBundle.message("waterEnabledLabel")
        return JPanel(BorderLayout(JBUI.scale(8), 0)).apply { isOpaque = false; add(box, BorderLayout.WEST); add(error, BorderLayout.CENTER) }
    }

    private fun section(titleKey: String, vararg fields: WaterField): JComponent {
        val box = JPanel(VerticalLayout(0)).apply { border = JBUI.Borders.empty(4, 0) }
        box.add(JBLabel(AbyssusBundle.message(titleKey)).apply { font = JBFont.label().asBold(); border = JBUI.Borders.empty(6, 16, 2, 16) })
        for (field in fields) box.add(fieldRow(field))
        return box
    }

    private fun fieldRow(field: WaterField): JComponent {
        val error = JBLabel("").apply { foreground = JBColor.RED; name = "water-error-${field.name}" }
        val editor = textEditor(state.texts.getValue(field), error) { text -> commit(field, text) }.apply {
            name = "water-field-${field.name}"
            font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
        }
        val editorAndError = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(editor, BorderLayout.CENTER)
            add(error, BorderLayout.EAST)
        }
        return JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(4, 16)
            add(JBLabel(waterFieldLabel(field)), GridBagConstraints().apply { weightx = 0.4; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST })
            add(editorAndError, GridBagConstraints().apply { weightx = 0.6; fill = GridBagConstraints.HORIZONTAL })
        }
    }

    /** Parses [text], checks it and writes it; anything refused leaves the file as it was. */
    private fun commit(field: WaterField, text: String): CommitOutcome = when (val parsed = input.parse(text)) {
        WaterInput.NotANumber -> CommitOutcome.Refused(AbyssusBundle.message("waterIssueNotNumber"))
        WaterInput.NotFinite -> CommitOutcome.Refused(waterIssueText(net.nevinsky.abyssus.lib.core.water.WaterIssue.NOT_FINITE))
        is WaterInput.Value -> outcome(edits.setField(target.file, target.id, field, parsed.number))
    }

    private fun outcome(edit: WaterEdit): CommitOutcome = when (edit) {
        WaterEdit.Changed -> CommitOutcome.Saved
        WaterEdit.Unchanged -> CommitOutcome.Unchanged
        is WaterEdit.Rejected -> CommitOutcome.Refused(waterErrorText(edit.error))
    }
}
