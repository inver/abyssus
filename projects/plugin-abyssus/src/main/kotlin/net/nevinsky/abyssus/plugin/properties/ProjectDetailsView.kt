/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.properties

import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import net.nevinsky.abyssus.lib.core.editor.document.copyWithBoolean
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.lib.core.editor.panel.FieldRef
import net.nevinsky.abyssus.lib.core.editor.panel.PanelBuilders
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings
import net.nevinsky.abyssus.plugin.filetype.projectSettingsProblems
import net.nevinsky.abyssus.plugin.projectView.DesignColors
import net.nevinsky.abyssus.plugin.sceneview.SceneViewSettings
import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel
import net.nevinsky.abyssus.plugin.ui.CommitOutcome
import net.nevinsky.abyssus.plugin.panel.RowHooks
import net.nevinsky.abyssus.plugin.panel.fieldRow
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The project file: the project settings and the main camera. The panel rebuilds this view from document and settings
 * events; the service validates again before every write. Controls are named `physics-enabled`, `scene-view-antialiasing`
 * and `show-fps`, and a settings problem `project-settings-problem`.
 */
internal class ProjectDetailsView @JvmOverloads constructor(
    private val state: PanelState.Project,
    private val settings: AbyssusProjectSettings,
    private val viewSettings: SceneViewSettings,
    private val parent: Disposable,
    collapsed: MutableMap<String, Boolean> = HashMap(),
) : JPanel(BorderLayout()) {
    private val hooks = object : RowHooks {
        override fun commit(ref: FieldRef, text: String): CommitOutcome {
            if (ref.field != "physicsEnabled") return CommitOutcome.Unchanged
            settings.setPhysicsEnabled(state.file, text.toBoolean())
            return CommitOutcome.Saved
        }

        override fun custom(key: String): JComponent? = when (key) {
            "antialiasing" -> antialiasing()
            "showFps" -> showFps()
            else -> null
        }

        override fun openScreenEditor() {}
    }

    init {
        val scenes = ProjectLayout.sceneFiles(state.file).map { it.nameWithoutExtension }
        val editable = if (state.settings.supported) setOf("physicsEnabled") else emptySet()
        // the settings reader decides what physicsEnabled means (an unsupported project reads as off)
        val root = copyWithBoolean(state.root, "physicsEnabled", state.settings.physicsEnabled)
        val model = PanelBuilders(EditorBundle, { false }).project(root, state.name, scenes, editable)
        val problem = if (state.settings.problems.isEmpty()) null else JBLabel(
            if (state.settings.supported) projectSettingsProblems(state.settings)
            else AbyssusBundle.message("propertiesProjectUnsupported", state.settings.problems.joinToString()),
        ).apply { name = "project-settings-problem"; border = JBUI.Borders.empty(8, 16) }
        val view = PanelModelView(
            model, hooks, collapsed, titleName = "project-name", after = listOfNotNull(problem),
        )
        view.sections.forEach { frame ->
            // the check box keeps the name of the control it replaces
            allComponents(frame).firstOrNull { it.name == "field-project-physicsEnabled" || it.name == "check-physicsEnabled" }?.name = "physics-enabled"
        }
        add(view, BorderLayout.CENTER)
    }

    private fun allComponents(c: java.awt.Component): List<java.awt.Component> =
        listOf(c) + ((c as? java.awt.Container)?.components?.flatMap { allComponents(it) } ?: emptyList())

    private fun hint(key: String) = JBLabel("<html>${AbyssusBundle.message(key)}</html>").apply {
        foreground = DesignColors.SECONDARY; font = JBFont.small()
    }

    private fun antialiasing(): JComponent {
        val combo = ComboBox(AntialiasingLevel.entries.toTypedArray()).apply {
            name = "scene-view-antialiasing"
            renderer = SimpleListCellRenderer.create("") { level ->
                level?.let { AbyssusBundle.message("antialiasingLevel${it.name}") } ?: ""
            }
            selectedItem = state.settings.antialiasing
            isEnabled = state.settings.supported
            addActionListener {
                if (isEnabled) (selectedItem as? AntialiasingLevel)?.let { settings.setAntialiasing(state.file, it) }
            }
        }
        return column(fieldRow(AbyssusBundle.message("propertiesProjectAntialiasing"), combo), hint("propertiesProjectAntialiasingHint"))
    }

    /** An IDE view setting, not a field of the .abss: it works for an unsupported project too. */
    private fun showFps(): JComponent {
        val box = JBCheckBox(AbyssusBundle.message("propertiesProjectShowFps"), viewSettings.showFps).apply {
            name = "show-fps"
            addActionListener { viewSettings.showFps = isSelected }
        }
        viewSettings.addShowFpsListener(parent) { value -> if (box.isSelected != value) box.isSelected = value }
        return column(box, hint("propertiesProjectShowFpsHint"))
    }

    private fun column(vararg parts: JComponent): JComponent = JPanel(VerticalLayout(JBUI.scale(2))).apply {
        isOpaque = false
        parts.forEach { add(it) }
    }
}
