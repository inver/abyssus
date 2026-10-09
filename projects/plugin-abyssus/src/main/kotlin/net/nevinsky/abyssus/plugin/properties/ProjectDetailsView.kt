/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.properties

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings
import javax.swing.JPanel

/** The panel rebuilds this view from document/settings events; the service validates again before every write. */
internal class ProjectDetailsView(state: PanelState.Project, settings: AbyssusProjectSettings) : JPanel(VerticalLayout(JBUI.scale(8))) {
    init {
        border = JBUI.Borders.empty(12, 16)
        add(JBLabel(state.name).apply { font = JBFont.label().asBold().biggerOn(1f); name = "project-name" })
        add(JBLabel(AbyssusBundle.message("propertiesProjectSubtitle")).apply { foreground = UIUtil.getContextHelpForeground() })
        add(JBCheckBox(AbyssusBundle.message("propertiesProjectPhysics"), state.settings.physicsEnabled).apply {
            name = "physics-enabled"
            isEnabled = state.settings.supported
            addActionListener { settings.setPhysicsEnabled(state.file, isSelected) }
        })
        add(JBLabel(AbyssusBundle.message("propertiesProjectPhysicsHint")).apply { foreground = UIUtil.getContextHelpForeground() })
        if (state.settings.problems.isNotEmpty()) add(JBLabel(
            if (state.settings.supported) AbyssusBundle.message("physicsEnabledNotBoolean")
            else AbyssusBundle.message("propertiesProjectUnsupported", state.settings.problems.joinToString())
        ).apply { name = "project-settings-problem" })
    }
}
