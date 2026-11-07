/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ColorIcon
import java.awt.BorderLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.modellights.LAMP_LIGHT_MAX
import net.nevinsky.abyssus.lib.core.editor.modellights.LampMaterialChoice
import net.nevinsky.abyssus.lib.core.editor.modellights.MaterialGeometry
import net.nevinsky.abyssus.lib.core.editor.modellights.lampMaterialChoices
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.ui.reportRejection

/**
 * Right-click "Create Lights From Model..." on a model entity row of a native scene: the user picks the material that
 * marks the lamps, and one point light per separate piece of it is added as one undoable command. The lights are
 * ordinary entities; they do not follow the model if it is moved later.
 */
open class CreateLightsFromModelAction : AbyssusTreeAction<ComponentTarget>() {
    init {
        templatePresentation.text = AbyssusBundle.message("createLightsTitle")
    }

    override fun targetOf(node: Any?): ComponentTarget? = entityTargetOf(node) { _, entity ->
        val render = entity.component(RenderComponent::class.java)
        render != null && render.type == MetaType.MODEL && render.assetName.isNotEmpty()
    }

    override fun isEnabled(e: AnActionEvent, target: ComponentTarget): Boolean {
        val project = e.project ?: return false
        val scene = SceneDocumentCache.of(project).read(target.file)?.scene ?: return false
        val asset = scene.component(target.entityId, RenderComponent::class.java)?.assetName ?: return false
        val assets = ProjectLayout.abssFor(target.file)?.parent?.findChild("assets") ?: return false
        return assets.findChild(asset)?.isDirectory == true
    }

    /** The geometry of the model's materials; parses the model, so it runs off the UI thread. Tests replace it. */
    internal open fun readGeometry(dir: File, asset: String, meshId: String?): List<MaterialGeometry>? =
        service<AbyssusPluginCtx>().assets.loading.modelLamps(dir, asset, meshId)

    /** The material the user picks, or null when the dialog is cancelled. Tests replace it. */
    internal open fun choose(project: Project, choices: List<LampMaterialChoice>): String? {
        val dialog = LampMaterialDialog(project, choices)
        return if (dialog.showAndGet()) {
            dialog.chosen
        } else {
            null
        }
    }

    override fun perform(project: Project, target: ComponentTarget, e: AnActionEvent) {
        val scene = SceneDocumentCache.of(project).read(target.file)?.scene ?: return
        val render = scene.component(target.entityId, RenderComponent::class.java) ?: return
        val dir = ProjectLayout.projectDirFor(target.file) ?: return
        // parses the model file, which the UI thread must not do
        val geometries =
            ProgressManager.getInstance()
                .runProcessWithProgressSynchronously<List<MaterialGeometry>?, RuntimeException>(
                    {
                        val meshId = render.meshId?.takeIf(String::isNotEmpty)
                        runReadAction { readGeometry(dir, render.assetName, meshId) }
                    },
                    AbyssusBundle.message("createLightsLoading"),
                    true,
                    project,
                )
        if (geometries == null) {
            Messages.showErrorDialog(
                project,
                AbyssusBundle.message("createLightsUnreadable"),
                AbyssusBundle.message("componentRejectedTitle"),
            )
            return
        }
        val choices = lampMaterialChoices(geometries)
        if (choices.isEmpty()) {
            Messages.showErrorDialog(
                project,
                AbyssusBundle.message("createLightsNone"),
                AbyssusBundle.message("componentRejectedTitle"),
            )
            return
        }
        val material = choose(project, choices) ?: return
        val geometry = geometries.firstOrNull { it.materialId == material } ?: return
        val added = SceneComponentEdits.createLightsFromModel(project, target.file, target.entityId, geometry)
        reportRejection(project, added.result)
        if (added.result == EditResult.Changed && added.merged) {
            Messages.showInfoMessage(
                project,
                AbyssusBundle.message("createLightsMerged", LAMP_LIGHT_MAX),
                AbyssusBundle.message("createLightsMergedTitle"),
            )
        }
    }
}

/** Picks the material that marks the lamps: emissive materials first, each with its color and lamp count. */
class LampMaterialDialog(project: Project, private val choices: List<LampMaterialChoice>) : DialogWrapper(project) {
    private val list = JBList(choices)

    /** The chosen material's id; meaningful once the dialog was closed with Create Lights. */
    val chosen: String?
        get() = list.selectedValue?.materialId

    init {
        title = AbyssusBundle.message("createLightsDialogTitle")
        setOKButtonText(AbyssusBundle.message("createLightsOk"))
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.selectedIndex = 0
        list.cellRenderer =
            object : ColoredListCellRenderer<LampMaterialChoice>() {
                override fun customizeCellRenderer(
                    list: JList<out LampMaterialChoice>,
                    value: LampMaterialChoice?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
                    value ?: return
                    value.emissive?.let { icon = ColorIcon(12, java.awt.Color(it.r, it.g, it.b)) }
                    append(value.materialId)
                    val count = AbyssusBundle.message("createLightsLamps", value.lamps)
                    append("  $count", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = list

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            add(JBLabel(AbyssusBundle.message("createLightsDialogLabel")), BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
            preferredSize = JBUI.size(380, 200)
        }
}
