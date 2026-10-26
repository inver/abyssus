/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneDocument
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageComponentEdits
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.plugin.dto.ProjectDto
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.filetype.editSceneJson
import net.nevinsky.abyssus.plugin.ui.showActionGroup

internal fun foliageChoices(project: Project, file: VirtualFile, entityId: String, metas: MetaFiles): List<String> {
    val root = SceneDocumentCache.of(project).read(file)?.root ?: return emptyList()
    val terrain =
        SceneDocument(root).BaseMeta(entityId)?.takeIf { it.isTerrain() } ?: return emptyList()
    val abss = ProjectLayout.abssFor(file) ?: return emptyList()
    if (AssetReadCache.of(project).read(abss)?.obj !is ProjectDto) return emptyList()
    return ProjectLayout.assetFolders(abss).mapNotNull { folder ->
        runCatchingKeepingCancellation {
            val meta = metas.inEditor(folder) ?: return@runCatchingKeepingCancellation null
            folder.name.takeIf {
                meta.json["type"]?.textValue() == MetaType.FOLIAGE.name &&
                        meta.json["additional"]?.get("terrain")?.textValue() == terrain.name
            }
        }.getOrNull()
    }
}

internal fun setFoliage(
    project: Project,
    file: VirtualFile,
    entityId: String,
    name: String,
    metas: MetaFiles
): Boolean {
    if (name !in foliageChoices(project, file, entityId, metas)) return false
    return editSceneJson(project, file, AbyssusBundle.message("commandAddFoliage")) { root ->
        FoliageComponentEdits().attach(root, entityId, name)
    }
}

/** The same terrain-bound choices serve the tree and the Scene view toolbar; an empty group means none fit. */
fun foliageGroup(project: Project, file: VirtualFile, entityId: String): DefaultActionGroup? {
    val root = SceneDocumentCache.of(project).read(file)?.root ?: return null
    if (SceneDocument(root).BaseMeta(entityId)?.isTerrain() != true) return null
    val metas = service<AbyssusPluginCtx>().assets.metaFiles
    val names = foliageChoices(project, file, entityId, metas)
    return DefaultActionGroup().apply {
        for (name in names) add(object : AnAction(), DumbAware {
            init {
                templatePresentation.setText(name, false)
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) {
                setFoliage(project, file, entityId, name, metas)
            }
        })
    }
}

open class AddFoliageAction : AbyssusTreeAction<ComponentTarget>() {
    init {
        templatePresentation.text = AbyssusBundle.message("addFoliageTitle")
    }

    override fun targetOf(node: Any?): ComponentTarget? {
        val entry = (node as? DtoEntryNode)?.value ?: return null
        val json = entry.value as? com.fasterxml.jackson.databind.JsonNode ?: return null
        val target = componentTargetOf(node)?.takeIf { it.kind == null } ?: return null
        return target.takeIf {
            net.nevinsky.abyssus.lib.core.editor.document.sceneEntityView(entry.name, json)
                .BaseMeta()?.isTerrain() == true
        }
    }

    override fun isEnabled(e: AnActionEvent, target: ComponentTarget): Boolean {
        val group = e.project?.let { foliageGroup(it, target.file, target.entityId) }
        val hasChoices = group != null && group.childrenCount > 0
        e.presentation.description = if (group != null && !hasChoices) AbyssusBundle.message("addFoliageNone") else null
        return hasChoices
    }

    override fun perform(project: Project, target: ComponentTarget, e: AnActionEvent) {
        val group = foliageGroup(project, target.file, target.entityId) ?: return
        showActionGroup(e, group, AbyssusBundle.message("addFoliageTitle"))
    }
}
