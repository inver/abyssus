/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import net.nevinsky.abyssus.lib.core.editor.panel.CustomRow
import net.nevinsky.abyssus.lib.core.editor.panel.FieldRef
import net.nevinsky.abyssus.lib.core.editor.panel.PanelBuilders
import net.nevinsky.abyssus.lib.core.editor.panel.Section
import net.nevinsky.abyssus.lib.core.editor.ray.RayModePhase
import net.nevinsky.abyssus.lib.core.editor.scene.scenePanelModel
import net.nevinsky.abyssus.lib.core.editor.scene.withSettingValue
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.SceneRayControls
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.facts.SceneFacts
import net.nevinsky.abyssus.plugin.filetype.setSceneToggle
import net.nevinsky.abyssus.plugin.panel.RowHooks
import net.nevinsky.abyssus.plugin.ui.CommitOutcome
import net.nevinsky.abyssus.plugin.ui.RayModeText

/**
 * A scene's Rendering settings. The **Ray Tracing** switch flips the same per-view mode as the Scene view's toolbar
 * toggle through [SceneRayControls], follows that mode (status, reason, Retry) while it changes, and with no Scene View
 * open it opens one; it writes nothing. Controls are named `ray-tracing-switch`, `ray-tracing-status`,
 * `ray-tracing-detail`, `ray-tracing-foliage` and `ray-tracing-retry`, which is how tests reach them.
 * `ray-tracing-foliage` carries the foliage note the open Scene view's Ray Control reports, which says its ray-traced
 * image leaves foliage out, while that view shows a scene with any.
 */
internal class SceneDetailsView
@JvmOverloads
constructor(
    private val controls: SceneRayControls,
    private val state: PanelState.UISceneState,
    parent: Disposable,
    /**
     * Opens the skybox chooser for the scene [state] shows and returns true when it assigned a skybox; replaced in
     * tests.
     */
    private val browseSkybox: (VirtualFile) -> Boolean = { chooseSceneSkybox(controls.project, it) },
    collapsed: MutableMap<String, Boolean> = HashMap(),
) : JPanel(BorderLayout()) {
    private val facts: SceneFacts<*> = controls
    private val file = state.file
    private val switch =
        JBCheckBox(AbyssusBundle.message("propertiesSceneRayTracing")).apply { name = "ray-tracing-switch" }
    private val status =
        JBLabel().apply {
            name = "ray-tracing-status"
            foreground = secondary()
        }
    private val detail =
        JBLabel().apply {
            name = "ray-tracing-detail"
            foreground = secondary()
            isVisible = false
        }
    private val foliage =
        JBLabel().apply {
            name = "ray-tracing-foliage"
            foreground = secondary()
            isVisible = false
        }
    private val retry =
        JButton(AbyssusBundle.message("sceneViewRayRetry")).apply {
            name = "ray-tracing-retry"
            isVisible = false
            isFocusable = false
        }
    private var updating = false

    private val skyboxModel = scenePanelModel(state.scene, ProjectLayout.abssFor(file) != null).skybox
    private val skybox = SkyboxRow(file, skyboxModel.value, skyboxModel.browsable, browseSkybox)

    private val hooks =
        object : RowHooks {
            override fun commit(ref: FieldRef, text: String): CommitOutcome =
                commitSceneSetting(controls.project, file, state.scene, ref, text)

            override fun custom(key: String): JComponent? =
                if (key == "rendering") {
                    renderingBody()
                } else {
                    null
                }

            override fun openScreenEditor() {}

            override fun assetField(key: String, label: String): JComponent? =
                if (key == "skyboxName") {
                    skybox.component(label)
                } else {
                    null
                }
        }

    init {
        val abss = ProjectLayout.abssFor(file)
        val base =
            PanelBuilders(EditorBundle, { false })
                .scene(
                    state.scene,
                    abss?.name.orEmpty(),
                    "scenes/${file.name}",
                )
        // the Rendering section (ray tracing) is not part of the board; it stays, framed like the others
        val rendering =
            Section(
                "scene.rendering",
                AbyssusBundle.message("propertiesSceneRendering"),
                "render",
                listOf(CustomRow("rendering")),
            )
        val view =
            PanelModelView(
                base.copy(
                    header = base.header.copy(title = state.name),
                    sections = base.sections + rendering,
                ),
                hooks,
                collapsed,
                titleName = "scene-name",
                onEnabled = { section, checked ->
                    section.enable?.settingKey?.let { setSceneToggle(controls.project, file, it, checked) }
                },
            )
        add(view, BorderLayout.CENTER)
        switch.addActionListener {
            if (!updating) {
                controls.request(file, switch.isSelected)
            }
        }
        retry.addActionListener { controls.retry(file) }
        controls.addListener(file, parent) { refresh() }
        refresh()
    }

    private fun secondary() = UIUtil.getContextHelpForeground()

    /** The ray tracing switch, its status and the saved limits: the body of the Rendering section. */
    private fun renderingBody(): JComponent {
        val row =
            JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                isOpaque = false
                add(switch, BorderLayout.WEST)
                add(retry, BorderLayout.EAST)
            }
        return JPanel(VerticalLayout(JBUI.scale(6))).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 0, 8, 0)
            add(row)
            add(status)
            add(detail)
            add(foliage)
            add(
                JBLabel(
                        "<html>${AbyssusBundle.message("propertiesSceneRayHint")} " +
                            "${AbyssusBundle.message("propertiesSceneRayNote")}</html>"
                    )
                    .apply {
                        foreground = secondary()
                        font = JBFont.small()
                        name = "ray-tracing-hint"
                    }
            )
            // G7: the saved ray limits (samples, rays per frame, bounces, instances) are no longer scene data, so they
            // have no rows
        }
    }

    /** Brings the switch, status, reason and Retry in line with the open view's mode (or with a pending request). */
    private fun refresh() {
        val mode = facts.rayMode(file.path)
        val pending = controls.isPending(file)
        updating = true
        try {
            switch.isSelected = mode?.requested == true || (mode == null && pending)
            switch.isEnabled = mode == null || mode.toggleEnabled
            switch.toolTipText = mode?.let(RayModeText::tooltip) ?: AbyssusBundle.message("sceneViewRayTracingTooltip")
            status.text =
                when {
                    mode == null && pending -> AbyssusBundle.message("propertiesSceneRayOpening")
                    mode == null -> ""
                    else -> RayModeText.status(mode) ?: ""
                }
            val reason =
                when (mode?.phase) {
                    RayModePhase.Unavailable -> mode.unavailable?.let(RayModeText::reason)
                    else -> null // a failure's reason is already part of the status line
                }
            detail.text = reason?.let { "<html>$it</html>" } ?: ""
            detail.isVisible = !reason.isNullOrBlank()
            val note = controls.foliageNote(file)
            foliage.text = note ?: ""
            foliage.isVisible = !note.isNullOrBlank()
            retry.isVisible = mode?.phase == RayModePhase.Failed
        } finally {
            updating = false
        }
    }
}

/** Opens the skybox chooser for [file], the scene Abyssus Properties shows; true when a skybox was assigned. */
internal fun chooseSceneSkybox(project: com.intellij.openapi.project.Project, file: VirtualFile): Boolean {
    val abss = ProjectLayout.abssFor(file) ?: return false
    val assets = com.intellij.openapi.components.service<net.nevinsky.abyssus.plugin.AbyssusPluginCtx>().assets
    val current =
        com.intellij.openapi.components
            .service<net.nevinsky.abyssus.plugin.AbyssusPluginCtx>()
            .documents
            .parsing
            .parse(textOf(file))
            .skyboxName
    return net.nevinsky.abyssus.plugin.projectView.chooseSkyboxFor(
        project,
        file,
        abss,
        current,
        assets.metaFiles,
        assets.hdrPreviews,
    )
}

/**
 * Saves the scene setting value [ref] names (ambient light, fog) with [text] as one undoable edit of [file]. [scene] is
 * what the panel shows, used to refuse bad text before anything is written. Other refs are [CommitOutcome.Unchanged].
 */
internal fun commitSceneSetting(
    project: com.intellij.openapi.project.Project,
    file: VirtualFile,
    scene: net.nevinsky.abyssus.lib.core.dto.SceneDto?,
    ref: FieldRef,
    text: String,
): CommitOutcome {
    if (ref.kind != net.nevinsky.abyssus.lib.core.editor.scene.SCENE_SETTING_KIND) {
        return CommitOutcome.Unchanged
    }
    val change = { dto: net.nevinsky.abyssus.lib.core.dto.SceneDto ->
        dto.withSettingValue(ref.field, text)
    }
    if (scene != null && change(scene) == null) {
        return CommitOutcome.Refused(AbyssusBundle.message("sceneSettingNotANumber"))
    }
    val written =
        net.nevinsky.abyssus.plugin.filetype.editSceneSettings(
            project, file, AbyssusBundle.message("commandEditSceneSetting"), change,
        )
    return if (written) CommitOutcome.Saved else CommitOutcome.Unchanged
}
