/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.ecs.scene.ComponentEditor
import net.nevinsky.abyssus.ecs.scene.EditResult
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.dto.textOf
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.dto.MetaFiles
import com.intellij.openapi.components.service
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.dto.SceneDocumentCache

/** Tells the user why an edit was refused; anything else needs no word. */
internal fun reportRejection(project: Project, result: EditResult) {
    if (result is EditResult.Rejected) {
        Messages.showErrorDialog(project, result.reason, AbyssusBundle.message("componentRejectedTitle"))
    }
}

/**
 * The choices for adding a component to [entityId] of [file]: one action per modeled kind the entity lacks, a Render
 * component being offered once per model or terrain of the project. Empty when the entity has everything.
 */
fun addComponentGroup(project: Project, file: VirtualFile, entityId: String, kinds: List<String>, metaFiles: MetaFiles): DefaultActionGroup {
    val group = DefaultActionGroup()
    for (name in kinds) {
        val label = ComponentEditor.kindOf(name)?.label ?: continue
        if (name == "RenderComponent") {
            val sub = DefaultActionGroup(label, true)
            val assets = SceneComponentEdits.renderAssets(file, metaFiles)
            if (assets.isEmpty()) sub.add(object : AnAction(AbyssusBundle.message("addComponentNoAssets")) {
                override fun actionPerformed(e: AnActionEvent) = Unit
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = false
                }
            })
            for (asset in assets) {
                // asset folders have underscores, which the menu would take for mnemonics
                sub.add(object : AnAction() {
                    init {
                        templatePresentation.setText("${asset.type.lowercase()} ${asset.name}", false)
                    }

                    override fun actionPerformed(e: AnActionEvent) = reportRejection(
                        project,
                        SceneComponentEdits.add(
                            project,
                            file,
                            entityId,
                            name,
                            metaFiles,
                            mapOf("assetType" to asset.type, "assetName" to asset.name),
                        ),
                    )
                })
            }
            group.add(sub)
        } else {
            group.add(object : AnAction(label) {
                override fun actionPerformed(e: AnActionEvent) =
                    reportRejection(project, SceneComponentEdits.add(project, file, entityId, name, metaFiles))
            })
        }
    }
    return group
}

internal fun selectedNode(e: AnActionEvent): Any? {
    val project = e.project ?: return null
    val pane = ProjectView.getInstance(project).currentProjectViewPane?.takeIf { it.id == AbyssusProjectViewPane.ID }
        ?: return null
    return TreeUtil.getUserObject(pane.selectedPath?.lastPathComponent)
}

/** Right-click "Add Component..." on an entity row: lists the modeled components it lacks. */
open class AddComponentAction : AbyssusTreeAction<ComponentTarget>() {
    override fun targetOf(node: Any?) = componentTargetOf(node)?.takeIf { it.kind == null }

    /** What can be added to the entity [target] stands for: the modeled kinds it lacks in the scene's current text. */
    internal fun choices(project: Project, target: ComponentTarget): DefaultActionGroup {
        val root = SceneDocumentCache.of(project).read(target.file)?.root ?: return DefaultActionGroup()
        val kinds = ComponentEditor.missingKinds(root, target.entityId).map { it.name }
        return addComponentGroup(project, target.file, target.entityId, kinds, service<AbyssusCore>().metaFiles)
    }

    override fun perform(project: Project, target: ComponentTarget, e: AnActionEvent) {
        val group = choices(project, target)
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                AbyssusBundle.message("addComponentTitle"),
                group,
                e.dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )
            .showInBestPositionFor(e.dataContext)
    }
}

/** Right-click "Remove Component" on a component row the plugin models. */
open class RemoveComponentAction : AbyssusTreeAction<ComponentTarget>() {
    override fun targetOf(node: Any?) =
        componentTargetOf(node)?.takeIf { it.kind != null && ComponentEditor.kindOf(it.kind) != null }

    override fun perform(project: Project, target: ComponentTarget, e: AnActionEvent) {
        reportRejection(project, SceneComponentEdits.remove(project, target.file, target.entityId, target.kind!!))
    }
}
