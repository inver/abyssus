/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import net.nevinsky.abyssus.plugin.AbyssusBundle
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.ButtonGroup
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton

/** The widget tree's share of the space left of the side panel (216 of 866 px at a 1190 px editor). */
private const val WIDGETS_SHARE = 0.25f

/** The share of the editor left of the side panel (866 of 1190 px). */
private const val CANVAS_SHARE = 0.728f

/**
 * The editable view of a UI asset: the widget tree on the left, the [canvas] with the
 * Edit / Preview switch in the middle, properties on the right. Every part reads and changes the one [controller].
 */
class UiEditorPanel(project: Project?, private val controller: UiEditorController, canvas: JComponent, scenes: () -> List<String> = { emptyList() }) : JPanel(BorderLayout()) {
    private val status = JBLabel(" ")
    private val zoomLabel = JBLabel(" ").apply { name = "canvas-zoom" }
    private val keys = JBLabel(" ").apply { name = "canvas-keys"; border = JBUI.Borders.empty(2, 6) }
    internal val resolution = JComboBox<String>().apply { name = "canvas-resolution"; toolTipText = AbyssusBundle.message("uiCanvasResolution") }
    private var building = false
    internal val screens = UiScreensMenu(controller, project)
    internal val widgets = UiWidgetTreePanel(controller)
    internal val properties = UiPropertiesPanel(controller)
    internal val declarations = UiDeclarationsPanel(project, controller)
    internal val preview = UiPreviewPanel(controller, scenes)
    internal val editButton = JToggleButton(AbyssusBundle.message("uiModeEdit"), true).apply { name = "mode-edit" }
    internal val previewButton = JToggleButton(AbyssusBundle.message("uiModePreview")).apply { name = "mode-preview" }

    init {
        ButtonGroup().apply { add(editButton); add(previewButton) }
        editButton.addActionListener { controller.setMode(UiEditorMode.EDIT) }
        previewButton.addActionListener { controller.setMode(UiEditorMode.PREVIEW) }
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply { add(screens); add(editButton); add(previewButton); add(resolution); add(zoomLabel); add(status) }
        resolution.addActionListener {
            if (building) return@addActionListener
            PREVIEW_PRESETS.getOrNull(resolution.selectedIndex)?.let { controller.setPreviewViewport(it.width, it.height) }
        }
        val centre = JPanel(BorderLayout()).apply { add(toolbar, BorderLayout.NORTH); add(canvas, BorderLayout.CENTER); add(keys, BorderLayout.SOUTH) }
        // at a 1190 px editor: about 216 px of widgets, 650 px of canvas, 292 px of properties
        widgets.minimumSize = Dimension(JBUI.scale(160), 0)
        centre.minimumSize = Dimension(JBUI.scale(360), 0)
        val inner = JBSplitter(false, WIDGETS_SHARE).apply { firstComponent = widgets; secondComponent = centre }
        val side = javax.swing.JTabbedPane().apply {
            addTab(AbyssusBundle.message("uiProperties"), properties)
            addTab(AbyssusBundle.message("uiDeclarations"), declarations)
            addTab(AbyssusBundle.message("uiPreviewTab"), preview)
        }
        side.minimumSize = Dimension(JBUI.scale(240), 0)
        val outer = JBSplitter(false, CANVAS_SHARE).apply { firstComponent = inner; secondComponent = side }
        add(outer, BorderLayout.CENTER)
        controller.addListener(::refreshStatus)
        controller.addZoomListener(::refreshZoom)
        refreshStatus()
        refreshZoom()
    }

    private fun refreshStatus() {
        editButton.isSelected = controller.mode == UiEditorMode.EDIT
        previewButton.isSelected = controller.mode == UiEditorMode.PREVIEW
        keys.text = AbyssusBundle.message(if (controller.mode == UiEditorMode.PREVIEW) "uiKeysPreview" else "uiKeysEdit")
        refreshResolution()
        status.text = controller.lastError?.let { AbyssusBundle.message("uiEditError.${it.name}") } ?: " "
    }

    private fun refreshZoom() {
        zoomLabel.text = AbyssusBundle.message("uiCanvasZoom", Math.round(controller.zoom * 100))
    }

    /** The picker lists the preview sizes, plus the current one when it is a custom size. */
    private fun refreshResolution() {
        val v = controller.previewViewport
        val labels = PREVIEW_PRESETS.map { "${it.width} \u00d7 ${it.height}" }
        val current = "${v.width} \u00d7 ${v.height}"
        val items = if (v in PREVIEW_PRESETS) labels else labels + current
        building = true
        try {
            if ((0 until resolution.itemCount).map { resolution.getItemAt(it) } != items) { resolution.removeAllItems(); items.forEach(resolution::addItem) }
            resolution.selectedIndex = items.indexOf(current)
        } finally {
            building = false
        }
    }
}
