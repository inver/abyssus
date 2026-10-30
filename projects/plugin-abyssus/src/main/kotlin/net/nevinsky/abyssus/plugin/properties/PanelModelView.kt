/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.ui.components.panels.VerticalLayout
import net.nevinsky.abyssus.lib.core.editor.panel.PanelModel
import net.nevinsky.abyssus.lib.core.editor.panel.Section
import net.nevinsky.abyssus.plugin.projectView.DesignColors
import net.nevinsky.abyssus.plugin.panel.HeaderPanel
import net.nevinsky.abyssus.plugin.panel.PropertySection
import net.nevinsky.abyssus.plugin.panel.RowHooks
import net.nevinsky.abyssus.plugin.panel.panelIcon
import net.nevinsky.abyssus.plugin.panel.rowComponent
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Draws a [PanelModel]: the header, one framed section per [Section] with its rows, and the footer note. The collapse
 * state of every section lives in [collapsed] (keyed by the section's key), which the panel keeps while the selection
 * stays on the same node and clears when it changes, so a rebuild after a file change keeps what the user collapsed.
 */
internal class PanelModelView(
    val model: PanelModel,
    private val hooks: RowHooks,
    private val collapsed: MutableMap<String, Boolean> = HashMap(),
    /** The component name of the header's title, which tests look up. */
    private val titleName: String = "header-title",
    private val onEnabled: (Section, Boolean) -> Unit = { _, _ -> },
    private val sectionActions: (Section) -> List<JComponent> = { emptyList() },
    private val sectionExtra: (Section) -> JComponent? = { null },
    /** Shown on the right of the header (the Undo and Redo of an asset). */
    private val headerExtra: JComponent? = null,
    /** Shown after the sections, before the footer (the Add component button, a hosted section). */
    private val after: List<JComponent> = emptyList(),
) : JPanel(BorderLayout()) {
    val sections: List<PropertySection>

    init {
        val box = JPanel(VerticalLayout(0))
        val header = HeaderPanel(model.header, panelIcon(model.header.iconKey), titleName)
        if (headerExtra != null) header.add(headerExtra, BorderLayout.EAST)
        box.add(header)
        sections = model.sections.map { section ->
            val body = JPanel(VerticalLayout(0)).apply { isOpaque = false; border = JBUI.Borders.empty(2, 0) }
            section.rows.forEach { body.add(rowComponent(it, hooks)) }
            sectionExtra(section)?.let { body.add(it) }
            val frame = PropertySection(
                section, section.iconKey?.let(::panelIcon), body, collapsed[section.key] == true,
                { collapsed[section.key] = it },
                if (section.enable != null) { checked -> onEnabled(section, checked) } else null,
                sectionActions(section),
            )
            box.add(frame)
            frame
        }
        after.forEach { box.add(it) }
        if (model.footer.isNotEmpty()) box.add(
            JBLabel("<html>${model.footer}</html>").apply {
                name = "panel-footer"; font = JBFont.small(); foreground = DesignColors.SECONDARY
                border = JBUI.Borders.empty(10, 16)
            },
        )
        add(box, BorderLayout.NORTH)
    }
}
