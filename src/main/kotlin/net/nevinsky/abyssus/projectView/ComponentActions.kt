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
import com.intellij.openapi.project.ProjectLocator
import net.nevinsky.abyssus.schema.ComponentSchemas
import net.nevinsky.abyssus.editor.components.EditResult
import net.nevinsky.abyssus.editor.document.SceneJson
import net.nevinsky.abyssus.dto.textOf
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
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
fun addComponentGroup(project: Project, file: VirtualFile, entityId: String, kinds: List<String>, metaFiles: MetaFiles): DefaultActionGroup =
    addComponentGroup(project, file, kinds, metaFiles) { name, initial ->
        reportRejection(project, SceneComponentEdits.add(project, file, entityId, name, metaFiles, initial))
    }

/**
 * The choices for adding a component of one of [kinds] in [file]'s project, a Render component being offered once per
 * model or terrain; [add] receives the kind and its initial field values (the asset of a render component).
 */
fun addComponentGroup(
    project: Project, file: VirtualFile, kinds: List<String>, metaFiles: MetaFiles,
    add: (kind: String, initial: Map<String, String>) -> Unit,
): DefaultActionGroup {
    val group = DefaultActionGroup()
    val editor = ComponentSchemas.of(project).editorFor(file)
    for (name in kinds) {
        val label = editor.kindOf(name)?.label ?: continue
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

                    override fun actionPerformed(e: AnActionEvent) =
                        add(name, mapOf("assetType" to asset.type, "assetName" to asset.name))
                })
            }
            group.add(sub)
        } else {
            group.add(object : AnAction(label) {
                override fun actionPerformed(e: AnActionEvent) = add(name, emptyMap())
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
        val kinds = ComponentSchemas.of(project).editorFor(target.file).missingKinds(root, target.entityId).map { it.name }
        return addComponentGroup(project, target.file, target.entityId, kinds, service<AbyssusCore>().assets.metaFiles)
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

/** The scene file whose `ecs` row [node] is, or null. The tree lists a wrapped scene's entities under it too. */
internal fun ecsRowSceneOf(node: Any?): VirtualFile? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val file = entry.source?.takeIf { it.isValid && it.extension == "scene" } ?: return null
    return file.takeIf { entry.name == "ecs" && entry.parentKeys.isEmpty() }
}

/**
 * Right-click "Add Component..." on a scene's `ecs` row: every modeled kind but Name; choosing one creates a new
 * entity (`Entity <id>`) holding it, as one undoable edit, and selects it.
 */
open class AddComponentOnEcsAction : AbyssusTreeAction<VirtualFile>() {
    init { templatePresentation.text = AbyssusBundle.message("addComponentAction") }

    override fun targetOf(node: Any?): VirtualFile? = ecsRowSceneOf(node)

    override fun isEnabled(e: AnActionEvent, target: VirtualFile) =
        e.project?.let { canAddLight(target, SceneDocumentCache.of(it)) } ?: false

    /** The kinds a new entity can start with, each creating one when chosen. */
    internal fun choices(project: Project, target: VirtualFile): DefaultActionGroup {
        val kinds = ComponentSchemas.of(project).editorFor(target).kinds.map { it.name }.filter { it != "NameComponent" }
        val metaFiles = service<AbyssusCore>().assets.metaFiles
        return addComponentGroup(project, target, kinds, metaFiles) { name, initial ->
            val added = SceneComponentEdits.addAsNewEntity(project, target, name, metaFiles, initial)
            reportRejection(project, added.result)
            added.entityId?.let { selectCreatedEntity(project, target, it) }
        }
    }

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        JBPopupFactory.getInstance()
            .createActionGroupPopup(AbyssusBundle.message("addComponentTitle"), choices(project, target), e.dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
            .showInBestPositionFor(e.dataContext)
    }
}

/** Right-click "Remove Component" on a component row the plugin models. */
open class RemoveComponentAction : AbyssusTreeAction<ComponentTarget>() {
    override fun targetOf(node: Any?) =
        componentTargetOf(node)?.takeIf { target ->
            val project = ProjectLocator.getInstance().guessProjectForFile(target.file) ?: return@takeIf false
            target.kind != null && ComponentSchemas.of(project).editorFor(target.file).kindOf(target.kind) != null
        }

    override fun perform(project: Project, target: ComponentTarget, e: AnActionEvent) {
        reportRejection(project, SceneComponentEdits.remove(project, target.file, target.entityId, target.kind!!))
    }
}
