/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.panel.pinFieldHeight
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.filetype.TreeActionIcons
import net.nevinsky.abyssus.plugin.panel.fieldRow
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The scene's `skybox` field: its saved name in a read-only box (`scene-skybox`) and, for a scene of a project, a 26 px
 * Browse button (`scene-skybox-browse`) that opens the chooser through [browse], which returns true when it assigned a skybox.
 */
internal class SkyboxRow(
    private val file: VirtualFile,
    value: String?,
    private val browsable: Boolean,
    private val browse: (VirtualFile) -> Boolean,
) {
    val field = JBTextField(text(value)).apply {
        name = "scene-skybox"; isEditable = false; columns = 16
        pinFieldHeight()
        accessibleContext.accessibleName = AbyssusBundle.message("propertiesSceneSkybox")
    }

    private fun text(value: String?) = value ?: AbyssusBundle.message("propertiesSceneSkyboxNone")

    fun component(label: String): JComponent {
        val line = JPanel(BorderLayout(JBUI.scale(6), 0)).apply { isOpaque = false; add(field, BorderLayout.CENTER) }
        if (browsable) {
            val tooltip = AbyssusBundle.message("propertiesSceneSkyboxBrowse")
            val button = JButton(TreeActionIcons.BROWSE).apply {
                name = "scene-skybox-browse"; toolTipText = tooltip; isFocusable = true
                accessibleContext.accessibleName = tooltip
                val side = JBUI.scale(26)
                preferredSize = Dimension(side, side)
            }
            button.addActionListener {
                if (browse(file)) field.text = text(parseDocument(textOf(file)).textAt("skyboxName"))
            }
            line.add(button, BorderLayout.EAST)
        }
        return fieldRow(label, line)
    }
}
