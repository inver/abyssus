/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.lib.core.editor.meta.PropertyRow
import net.nevinsky.abyssus.lib.core.editor.meta.RowKind
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

private const val NAME_COLUMN = 150

/** The accent of the design canvas (`#3fb8c9` on dark), darkened for light themes. */
private val ACCENT = JBColor(Color(0x1E8A99), Color(0x3FB8C9))

internal fun columnHeader(): JComponent = twoColumns(
    JBLabel(AbyssusBundle.message("propertiesNameColumn").uppercase()).apply {
        foreground = secondaryColor(); font = JBFont.small()
    },
    JBLabel(AbyssusBundle.message("propertiesValueColumn").uppercase()).apply {
        foreground = secondaryColor(); font = JBFont.small()
    },
).apply {
    border = BorderFactory.createCompoundBorder(
        JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
        JBUI.Borders.empty(6, 16)
    )
}

internal fun rowOf(row: PropertyRow): JComponent = when (row.kind) {
    RowKind.HEADING -> twoColumns(JBLabel(row.name).apply { foreground = ACCENT }, JBLabel("")).apply {
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(10, 16, 4, 16)
        )
    }

    else -> {
        val indent = if (row.kind == RowKind.ADDITIONAL) "    " else ""
        val value = JBLabel(row.value).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size); toolTipText =
            row.value.ifEmpty { null }
        }
        twoColumns(JBLabel(indent + row.name), value).apply { border = JBUI.Borders.empty(4, 16) }
    }
}

internal fun twoColumns(name: JComponent, value: JComponent): JPanel = JPanel(GridBagLayout()).apply {
    add(
        name,
        GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.NORTHWEST; ipadx = 0 }
            .also { name.preferredSize = Dimension(JBUI.scale(NAME_COLUMN), name.preferredSize.height) })
    add(
        value,
        GridBagConstraints().apply {
            gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.NORTHWEST
        })
}
