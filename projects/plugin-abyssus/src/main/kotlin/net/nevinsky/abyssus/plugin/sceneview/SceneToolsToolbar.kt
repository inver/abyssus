/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.lib.core.editor.pick.GizmoMode
import net.nevinsky.abyssus.lib.core.editor.pick.GizmoSpace
import net.nevinsky.abyssus.lib.core.editor.pick.SceneInteraction
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.ui.JBColor

/**
 * The action toolbars of the Scene view's mockup groups: the Select / Move / Rotate tools, Local-World and Snap, the
 * display controls (shading, Shadows, Fog, Grid) and Frame selection. The actions are created for one view and never
 * registered, so they may hold that view's [interaction] and [model]; they read [toolbarState] through [state].
 * Everything runs on the EDT, which is also where the actions update.
 */
internal class SceneToolsToolbar(
    private val interaction: SceneInteraction,
    private val model: SceneViewToolsModel,
    private val state: () -> ToolbarState,
    private val frameSelection: () -> Unit,
    private val target: JComponent,
) {
    private val bars = mutableListOf<ActionToolbar>()

    /** The tool, space/snap and display groups in toolbar order, with a divider between them. */
    fun components(): List<JComponent> = listOf(
        bar("AbyssusSceneTools", DefaultActionGroup(
            modeAction(GizmoMode.SELECT, SceneViewIcon.SELECT, "sceneViewSelectTooltip") { it.gizmoEnabled },
            modeAction(GizmoMode.MOVE, SceneViewIcon.MOVE, "sceneViewMoveTooltip") { it.gizmoEnabled },
            modeAction(GizmoMode.ROTATE, SceneViewIcon.ROTATE, "sceneViewRotateTooltip") { it.gizmoEnabled },
        )),
        divider(),
        bar("AbyssusSceneSpace", DefaultActionGroup(SpaceAction(), SnapAction())),
        divider(),
        bar("AbyssusSceneDisplay", DefaultActionGroup(
            ShadingAction(),
            layerAction(SceneViewIcon.SHADOWS, "sceneViewShadowsTooltip", { model.display.shadows }, model::setShadows) { it.shadowsEnabled },
            layerAction(SceneViewIcon.FOG, "sceneViewFogTooltip", { model.display.fog }, model::setFog) { it.fogEnabled },
            layerAction(SceneViewIcon.GRID, "sceneViewGridTooltip", { model.display.grid }, model::setGrid) { it.gridEnabled },
        )),
    )

    /** The Frame selection button, placed after the camera chooser. */
    fun frameComponent(): JComponent = bar("AbyssusSceneFrame", DefaultActionGroup(FrameAction()))

    /** Re-evaluates every action; call after the state it reads changed. */
    fun update() = bars.forEach { it.updateActionsAsync() }

    private fun bar(place: String, group: DefaultActionGroup): JComponent {
        val toolbar = ActionManager.getInstance().createActionToolbar(place, group, true)
        toolbar.targetComponent = target
        toolbar.isReservePlaceAutoPopupIcon = false
        bars += toolbar
        return toolbar.component.apply { name = place; isOpaque = false; border = JBUI.Borders.empty() }
    }

    /** A thin vertical line between toolbar groups. */
    fun divider(): JComponent = object : JComponent() {
        override fun paintComponent(g: java.awt.Graphics) {
            g.color = JBColor.border()
            g.fillRect(width / 2, JBUI.scale(2), JBUI.scale(1), height - JBUI.scale(4))
        }
    }.apply {
        name = DIVIDER_NAME
        isOpaque = false
        preferredSize = Dimension(JBUI.scale(9), JBUI.scale(20))
    }

    private fun modeAction(mode: GizmoMode, icon: SceneViewIcon, textKey: String, enabled: (ToolbarState) -> Boolean) =
        object : ToggleAction(AbyssusBundle.message(textKey), null, icon.icon), DumbAware {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun isSelected(e: AnActionEvent) = interaction.mode == mode
            override fun setSelected(e: AnActionEvent, selected: Boolean) { if (selected) interaction.mode = mode }
            override fun update(e: AnActionEvent) {
                super.update(e)
                e.presentation.isEnabled = enabled(state())
            }
        }

    private fun layerAction(icon: SceneViewIcon, textKey: String, current: () -> Boolean, set: (Boolean) -> Unit, enabled: (ToolbarState) -> Boolean) =
        object : ToggleAction(AbyssusBundle.message(textKey), null, icon.icon), DumbAware {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun isSelected(e: AnActionEvent) = current()
            override fun setSelected(e: AnActionEvent, selected: Boolean) = set(selected)
            override fun update(e: AnActionEvent) {
                super.update(e)
                e.presentation.isEnabled = enabled(state())
            }
        }

    private inner class SnapAction : ToggleAction(null, AbyssusBundle.message("sceneViewSnapTooltip"), SceneViewIcon.SNAP.icon), DumbAware {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun isSelected(e: AnActionEvent) = model.snapOn
        override fun setSelected(e: AnActionEvent, selected: Boolean) { model.snapOn = selected }
        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.text = AbyssusBundle.message("sceneViewSnap", formatStep(model.snapStep))
            e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
            e.presentation.isEnabled = state().snapEnabled
        }
    }

    private inner class SpaceAction : ComboBoxAction(), DumbAware {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun createPopupActionGroup(button: JComponent): DefaultActionGroup = DefaultActionGroup(
            GizmoSpace.entries.map { space -> choice(spaceLabel(space)) { model.space = space } },
        )
        override fun update(e: AnActionEvent) {
            e.presentation.text = spaceLabel(model.space)
            e.presentation.description = AbyssusBundle.message("sceneViewSpaceTooltip")
            e.presentation.isEnabled = state().spaceEnabled
        }
    }

    private inner class ShadingAction : ComboBoxAction(), DumbAware {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun createPopupActionGroup(button: JComponent): DefaultActionGroup = DefaultActionGroup(
            Shading.entries.map { shading -> choice(shadingLabel(shading)) { model.setShading(shading) } },
        )
        override fun update(e: AnActionEvent) {
            e.presentation.text = shadingLabel(model.display.shading)
            e.presentation.description = AbyssusBundle.message("sceneViewShadingTooltip")
            e.presentation.isEnabled = state().shadingEnabled
        }
    }

    private inner class FrameAction : AnAction(AbyssusBundle.message("sceneViewFrameTooltip"), null, SceneViewIcon.FRAME.icon), DumbAware {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun actionPerformed(e: AnActionEvent) = frameSelection()
        override fun update(e: AnActionEvent) { e.presentation.isEnabled = state().frameEnabled }
    }

    private fun choice(text: String, run: () -> Unit): AnAction = object : AnAction(text), DumbAware {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun actionPerformed(e: AnActionEvent) = run()
    }

    private fun spaceLabel(space: GizmoSpace) = AbyssusBundle.message(if (space == GizmoSpace.WORLD) "sceneViewSpaceWorld" else "sceneViewSpaceLocal")
    private fun shadingLabel(shading: Shading) = AbyssusBundle.message(when (shading) {
        Shading.LIT -> "sceneViewShadingLit"
        Shading.UNLIT -> "sceneViewShadingUnlit"
        Shading.WIREFRAME -> "sceneViewShadingWireframe"
    })

    companion object {
        /** The component name of a divider between toolbar groups. */
        const val DIVIDER_NAME = "toolbar-divider"
    }
}

/** `0.5` for a half step, `1` for a whole one: no trailing zeros. */
internal fun formatStep(step: Float): String = if (step == step.toInt().toFloat()) step.toInt().toString() else step.toString()
