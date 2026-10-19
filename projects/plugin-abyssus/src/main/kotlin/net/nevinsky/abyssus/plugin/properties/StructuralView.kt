/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.editor.panel.FieldRef
import net.nevinsky.abyssus.lib.core.editor.scene.scenePanelModel
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.filetype.setSceneToggle
import net.nevinsky.abyssus.plugin.projectView.UiScreenOpener
import net.nevinsky.abyssus.plugin.ui.CommitOutcome
import net.nevinsky.abyssus.plugin.panel.RowHooks
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A folder, the `ecs` row, a scene setting or a UI screen. A folder and `ecs` are read only. A setting has its `Enabled` box
 * (one undoable edit of `<x>Enabled`) and, for the skybox, the Browse button of the scene view; a screen has the link that
 * opens the Screen Editor on it.
 */
internal class StructuralView @JvmOverloads constructor(
    private val project: Project,
    private val state: PanelState.StructuralDetails,
    collapsed: MutableMap<String, Boolean> = HashMap(),
    private val browseSkybox: (VirtualFile) -> Boolean = { chooseSceneSkybox(project, it) },
) : JPanel(BorderLayout()) {
    private val selection = state.selection

    private val hooks = object : RowHooks {
        override fun commit(ref: FieldRef, text: String): CommitOutcome = CommitOutcome.Unchanged
        override fun custom(key: String): JComponent? = null
        override fun openScreenEditor() {
            val screen = selection as? Structural.Screen ?: return
            screen.meta?.let { project.service<UiScreenOpener>().open(it, screen.screenId) }
        }

        override fun assetField(key: String, label: String): JComponent? {
            val setting = selection as? Structural.Setting ?: return null
            if (key != "skyboxName") return null
            val model = state.root?.let { scenePanelModel(it, ProjectLayout.abssFor(setting.file) != null).skybox }
            return SkyboxRow(setting.file, model?.value, model?.browsable == true, browseSkybox).component(label)
        }
    }

    init {
        val view = PanelModelView(
            state.model, hooks, collapsed, titleName = "structural-title",
            onEnabled = { section, checked ->
                val setting = selection as? Structural.Setting
                val key = section.enable?.settingKey
                if (setting != null && key != null) setSceneToggle(project, setting.file, key, checked)
            },
        )
        add(view, BorderLayout.CENTER)
    }
}
