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
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.ecs.scene.LightEntities
import net.nevinsky.abyssus.ecs.scene.LightPreset
import net.nevinsky.abyssus.editor.document.SceneJson
import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.dto.textOf
import net.nevinsky.abyssus.editor.document.SceneEntityTree
import net.nevinsky.abyssus.dto.SceneDocumentCache

/** The same three choices in the tree and toolbar; placement is read when a choice is made. */
class AddLightGroup(
    project: Project,
    file: VirtualFile,
    position: () -> Vec3,
    select: (String) -> Unit = { selectCreatedEntity(project, file, it) },
) : DefaultActionGroup(AbyssusBundle.message("addLightTitle"), true), DumbAware {
    init {
        for (preset in LightPreset.entries) add(object : AnAction(AbyssusBundle.message(preset.labelKey)), DumbAware {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = canAddLight(file, SceneDocumentCache.of(project))
            }
            override fun actionPerformed(e: AnActionEvent) {
                val added = SceneComponentEdits.addLight(project, file, preset, position(), SceneDocumentCache.of(project))
                reportRejection(project, added.result)
                added.entityId?.let(select)
            }
        })
    }
}

/** Publish a new entity immediately, then select its row when the asynchronous tree refresh reaches it. */
internal fun selectCreatedEntity(project: Project, file: VirtualFile, entityId: String) {
    val tree = runCatchingKeepingCancellation { SceneEntityTree(SceneJson().parse(textOf(file))) }.getOrNull()
    val entity = tree?.entities()?.get(entityId)
    if (entity?.isObject == true) {
        val node = DtoEntryNode(project, file.path, DtoRow(entityId, entity), file, tree.entityKeys())
        AbyssusSelection.of(project).select(node)
    }
    selectEntityInAbyssusView(project, file, entityId)
}

internal fun canAddLight(file: VirtualFile, cache: SceneDocumentCache): Boolean =
    cache.read(file)?.let { LightEntities.canAdd(it.root) } ?: false

/** Only scene rows can create an entity; the tree uses the origin as its placement point. */
open class AddLightAction : AbyssusTreeAction<VirtualFile>() {
    init { templatePresentation.text = AbyssusBundle.message("addLightTitle") }

    override fun targetOf(node: Any?): VirtualFile? = viewableSceneFile(node)

    override fun isEnabled(e: AnActionEvent, target: VirtualFile) = e.project?.let { canAddLight(target, SceneDocumentCache.of(it)) } ?: false

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        JBPopupFactory.getInstance().createActionGroupPopup(
            AbyssusBundle.message("addLightTitle"),
            AddLightGroup(project, target, { Vec3(0f, 0f, 0f) }),
            e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
        ).showInBestPositionFor(e.dataContext)
    }
}
