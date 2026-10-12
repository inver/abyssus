/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * The editable view of a UI asset: screens, the widget tree and the palette on the left, the [canvas] with the
 * Edit / Preview switch in the middle, properties on the right. Every part reads and changes the one [controller].
 */
class UiEditorPanel(project: Project?, private val controller: UiEditorController, canvas: JComponent, scenes: () -> List<String> = { emptyList() }) : JPanel(BorderLayout()) {
    private val status = JBLabel(" ")
    internal val screens = UiScreensPanel(project, controller)
    internal val widgets = UiWidgetTreePanel(controller)
    internal val palette = UiPalettePanel(controller)
    internal val properties = UiPropertiesPanel(controller)
    internal val declarations = UiDeclarationsPanel(project, controller)
    internal val preview = UiPreviewPanel(controller, scenes)
    internal val editButton = JToggleButton(AbyssusBundle.message("uiModeEdit"), true).apply { name = "mode-edit" }
    internal val previewButton = JToggleButton(AbyssusBundle.message("uiModePreview")).apply { name = "mode-preview" }

    init {
        ButtonGroup().apply { add(editButton); add(previewButton) }
        editButton.addActionListener { controller.setMode(UiEditorMode.EDIT) }
        previewButton.addActionListener { controller.setMode(UiEditorMode.PREVIEW) }
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply { add(editButton); add(previewButton); add(status) }
        val centre = JPanel(BorderLayout()).apply { add(toolbar, BorderLayout.NORTH); add(canvas, BorderLayout.CENTER) }
        val left = JPanel(BorderLayout()).apply {
            add(screens, BorderLayout.NORTH)
            add(widgets, BorderLayout.CENTER)
            add(palette, BorderLayout.SOUTH)
        }
        val inner = JBSplitter(false, 0.28f).apply { firstComponent = left; secondComponent = centre }
        val side = javax.swing.JTabbedPane().apply {
            addTab(AbyssusBundle.message("uiProperties"), properties)
            addTab(AbyssusBundle.message("uiDeclarations"), declarations)
            addTab(AbyssusBundle.message("uiPreviewTab"), preview)
        }
        val outer = JBSplitter(false, 0.74f).apply { firstComponent = inner; secondComponent = side }
        add(outer, BorderLayout.CENTER)
        controller.addListener(::refreshStatus)
        refreshStatus()
    }

    private fun refreshStatus() {
        editButton.isSelected = controller.mode == UiEditorMode.EDIT
        previewButton.isSelected = controller.mode == UiEditorMode.PREVIEW
        status.text = controller.lastError?.let { AbyssusBundle.message("uiEditError.${it.name}") } ?: " "
    }
}
