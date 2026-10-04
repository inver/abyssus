/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.properties

import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.filetype.SceneIcons
import net.nevinsky.abyssus.sceneview.RayModePhase
import net.nevinsky.abyssus.sceneview.RayModeText
import net.nevinsky.abyssus.sceneview.SceneRayControls
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A scene's runtime view settings: the **Ray Tracing** switch. It flips the same per-view mode as the Scene view's
 * toolbar toggle through [SceneRayControls], follows that mode (status, reason, Retry) while it changes, and with no Scene
 * View open it opens one. Nothing here reads or writes the scene file. Controls are named `ray-tracing-switch`,
 * `ray-tracing-status`, `ray-tracing-detail` and `ray-tracing-retry`, which is how tests reach them.
 */
internal class SceneDetailsView(private val controls: SceneRayControls, private val state: PanelState.SceneDetails, parent: Disposable) : JPanel(BorderLayout()) {
    private val file = state.file
    private val switch = JBCheckBox(AbyssusBundle.message("propertiesSceneRayTracing")).apply { name = "ray-tracing-switch" }
    private val status = JBLabel().apply { name = "ray-tracing-status"; foreground = secondary() }
    private val detail = JBLabel().apply { name = "ray-tracing-detail"; foreground = secondary(); isVisible = false }
    private val retry = JButton(AbyssusBundle.message("sceneViewRayRetry")).apply { name = "ray-tracing-retry"; isVisible = false; isFocusable = false }
    private var updating = false

    init {
        val box = JPanel(VerticalLayout(0))
        box.add(header())
        box.add(section())
        add(box, BorderLayout.NORTH)
        switch.addActionListener { if (!updating) controls.request(file, switch.isSelected) }
        retry.addActionListener { controls.retry(file) }
        controls.addListener(file, parent) { refresh() }
        refresh()
    }

    private fun secondary() = UIUtil.getContextHelpForeground()

    private fun header(): JComponent {
        val text = JPanel(VerticalLayout(JBUI.scale(2))).apply {
            add(JBLabel(state.name).apply { font = JBFont.label().asBold().biggerOn(1f); name = "scene-name" })
            add(JBLabel(AbyssusBundle.message("propertiesSceneSubtitle")).apply { foreground = secondary() })
        }
        return JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
            border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0), JBUI.Borders.empty(12, 16))
            add(JBLabel(SceneIcons.FILE), BorderLayout.WEST)
            add(text, BorderLayout.CENTER)
        }
    }

    private fun section(): JComponent {
        val row = JPanel(BorderLayout(JBUI.scale(8), 0)).apply { isOpaque = false; add(switch, BorderLayout.WEST); add(retry, BorderLayout.EAST) }
        return JPanel(VerticalLayout(JBUI.scale(6))).apply {
            border = JBUI.Borders.empty(12, 16, 16, 16)
            add(JBLabel(AbyssusBundle.message("propertiesSceneRendering").uppercase()).apply { foreground = secondary(); font = JBFont.small() })
            add(row)
            add(status)
            add(detail)
            add(JBLabel("<html>${AbyssusBundle.message("propertiesSceneRayHint")} ${AbyssusBundle.message("propertiesSceneRayNote")}</html>").apply {
                foreground = secondary(); font = JBFont.small(); name = "ray-tracing-hint"
            })
        }
    }

    /** Brings the switch, status, reason and Retry in line with the open view's mode (or with a pending request). */
    private fun refresh() {
        val mode = controls.mode(file)
        val pending = controls.isPending(file)
        updating = true
        try {
            switch.isSelected = mode?.requested == true || (mode == null && pending)
            switch.isEnabled = mode == null || mode.toggleEnabled
            switch.toolTipText = mode?.let(RayModeText::tooltip) ?: AbyssusBundle.message("sceneViewRayTracingTooltip")
            status.text = when {
                mode == null && pending -> AbyssusBundle.message("propertiesSceneRayOpening")
                mode == null -> ""
                else -> RayModeText.status(mode) ?: ""
            }
            val reason = when (mode?.phase) {
                RayModePhase.Unavailable -> mode.unavailable?.let(RayModeText::reason)
                else -> null // a failure's reason is already part of the status line
            }
            detail.text = reason?.let { "<html>$it</html>" } ?: ""
            detail.isVisible = !reason.isNullOrBlank()
            retry.isVisible = mode?.phase == RayModePhase.Failed
        } finally {
            updating = false
        }
    }
}
