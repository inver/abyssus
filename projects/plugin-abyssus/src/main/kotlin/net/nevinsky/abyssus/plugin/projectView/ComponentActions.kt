/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.schema.ComponentSchemas
import net.nevinsky.abyssus.plugin.ui.reportRejection
import net.nevinsky.abyssus.plugin.ui.showActionGroup

/**
 * The choices for adding a component to [entityId] of [file]: one action per modeled kind the entity lacks, a Render
 * component being offered once per model or terrain of the project. Empty when the entity has everything.
 */
fun addComponentGroup(
    project: Project,
    file: VirtualFile,
    entityId: String,
    kinds: List<String>,
    metaFiles: MetaFiles
): DefaultActionGroup =
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
            if (assets.isEmpty()) {
                sub.add(object : AnAction(AbyssusBundle.message("addComponentNoAssets")) {
                    override fun actionPerformed(e: AnActionEvent) = Unit
                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = false
                    }
                })
            }
            for (asset in assets) {
                // asset folders have underscores, which the menu would take for mnemonics
                sub.add(object : AnAction() {
                    init {
                        templatePresentation.setText("${asset.type.name.lowercase()} ${asset.name}", false)
                    }

                    override fun actionPerformed(e: AnActionEvent) =
                        add(name, mapOf("assetType" to asset.type.name, "assetName" to asset.name))
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
    internal fun choices(project: Project, target: ComponentTarget, metaFiles: MetaFiles): DefaultActionGroup {
        val root = SceneDocumentCache.of(project).read(target.file)?.root ?: return DefaultActionGroup()
        val kinds =
            ComponentSchemas.of(project).editorFor(target.file).missingKinds(root, target.entityId).map { it.name }
        return addComponentGroup(project, target.file, target.entityId, kinds, metaFiles)
    }

    override fun perform(project: Project, target: ComponentTarget, e: AnActionEvent) {
        val group = choices(project, target, service<AbyssusPluginCtx>().assets.metaFiles)
        showActionGroup(e, group, AbyssusBundle.message("addComponentTitle"))
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
    init {
        templatePresentation.text = AbyssusBundle.message("addComponentAction")
    }

    override fun targetOf(node: Any?): VirtualFile? = ecsRowSceneOf(node)

    override fun isEnabled(e: AnActionEvent, target: VirtualFile) =
        e.project?.let { canAddLight(target, SceneDocumentCache.of(it)) } ?: false

    /** The kinds a new entity can start with, each creating one when chosen. */
    internal fun choices(project: Project, target: VirtualFile, metaFiles: MetaFiles): DefaultActionGroup {
        val kinds =
            ComponentSchemas.of(project).editorFor(target).kinds.map { it.name }.filter { it != "NameComponent" }
        return addComponentGroup(project, target, kinds, metaFiles) { name, initial ->
            val added = SceneComponentEdits.addAsNewEntity(project, target, name, metaFiles, initial)
            reportRejection(project, added.result)
            added.entityId?.let { selectCreatedEntity(project, target, it) }
        }
    }

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        showActionGroup(
            e,
            choices(project, target, service<AbyssusPluginCtx>().assets.metaFiles),
            AbyssusBundle.message("addComponentTitle")
        )
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

/**
 * Right-click "Remove Entity" on an entity row of a native scene: removes that entity only, as one undoable command.
 * Another entity that still refers to it, or an arrangement that owns copies, refuses the removal with a message.
 */
open class RemoveEntityAction : AbyssusTreeAction<ComponentTarget>() {
    override fun targetOf(node: Any?) = componentTargetOf(node)?.takeIf { it.kind == null }

    override fun perform(project: Project, target: ComponentTarget, e: AnActionEvent) {
        reportRejection(project, SceneComponentEdits.removeEntity(project, target.file, target.entityId))
    }
}
