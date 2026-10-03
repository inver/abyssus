/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.projectView

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.SceneReader
import com.intellij.openapi.components.service
import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import net.nevinsky.abyssus.ecs.scene.LightEntities
import net.nevinsky.abyssus.ecs.scene.LightPreset
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.sceneview.Vec3
import net.nevinsky.abyssus.sceneview.textOf

/** The same three choices in the tree and toolbar; placement is read when a choice is made. */
class AddLightGroup(
    project: Project,
    file: VirtualFile,
    position: () -> Vec3,
    select: (String) -> Unit = { selectCreatedLight(project, file, it) },
) : DefaultActionGroup(AbyssusBundle.message("addLightTitle"), true), DumbAware {
    init {
        for (preset in LightPreset.entries) add(object : AnAction(AbyssusBundle.message(preset.labelKey)), DumbAware {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = canAddLight(file)
            }
            override fun actionPerformed(e: AnActionEvent) {
                val added = SceneComponentEdits.addLight(project, file, preset, position())
                reportRejection(project, added.result)
                added.entityId?.let(select)
            }
        })
    }
}

/** Publish immediately, then select the corresponding row when the asynchronous tree refresh reaches it. */
private fun selectCreatedLight(project: Project, file: VirtualFile, entityId: String) {
    val entity = runCatchingKeepingCancellation {
        SceneJson.parse(textOf(file)).get("ecs")?.get("entities")?.get(entityId)
    }.getOrNull()
    if (entity?.isObject == true) {
        val node = DtoEntryNode(project, file.path, DtoRow(entityId, entity), file, listOf("ecs", "entities"))
        AbyssusSelection.of(project).select(node)
    }
    selectEntityInAbyssusView(project, file, entityId)
}

internal fun canAddLight(file: VirtualFile): Boolean = runCatchingKeepingCancellation {
    if (!file.isValid) return@runCatchingKeepingCancellation false
    val text = textOf(file)
    service<SceneReader>().parse(text)
    LightEntities.canAdd(SceneJson.parse(text))
}.getOrDefault(false)

/** Only scene rows can create an entity; the tree uses the origin as its placement point. */
open class AddLightAction : AnAction(), DumbAware {
    init { templatePresentation.text = AbyssusBundle.message("addLightTitle") }
    internal open fun selected(e: AnActionEvent): Any? = selectedNode(e)
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    private fun file(e: AnActionEvent): VirtualFile? = when (val node = selected(e)) {
        is DtoEntryNode -> sceneFileOf(node.value)
        is AbyssusAssetNode -> node.virtualFile.takeIf(ProjectLayout::isScene)
        else -> null
    }
    override fun update(e: AnActionEvent) {
        val scene = file(e)
        e.presentation.isVisible = scene != null
        e.presentation.isEnabled = scene != null && canAddLight(scene)
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = file(e) ?: return
        if (!canAddLight(file)) return
        JBPopupFactory.getInstance().createActionGroupPopup(
            AbyssusBundle.message("addLightTitle"),
            AddLightGroup(project, file, { Vec3(0f, 0f, 0f) }),
            e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
        ).showInBestPositionFor(e.dataContext)
    }
}
