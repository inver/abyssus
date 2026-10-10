/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.plugin.dto.*
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.plugin.filetype.editSceneJson
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneDocument
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree

internal fun foliageChoices(project: Project, file: VirtualFile, entityId: String, metas: MetaFiles): List<String> {
    val root = SceneDocumentCache.of(project).read(file)?.root ?: return emptyList()
    val terrain = SceneDocument(root).renderAsset(entityId)?.takeIf { it.type == MetaType.TERRAIN.name } ?: return emptyList()
    val abss = ProjectLayout.abssFor(file) ?: return emptyList()
    if (AssetReadCache.of(project).read(abss)?.obj !is ProjectDto) return emptyList()
    return ProjectLayout.assetFolders(abss).mapNotNull { folder ->
        runCatchingKeepingCancellation {
            val meta = metas.inEditor(folder) ?: return@runCatchingKeepingCancellation null
            folder.name.takeIf { meta.json["type"]?.textValue() == MetaType.FOLIAGE.name &&
                meta.json["additional"]?.get("terrain")?.textValue() == terrain.name }
        }.getOrNull()
    }
}

internal fun setFoliage(project: Project, file: VirtualFile, entityId: String, name: String, metas: MetaFiles): Boolean {
    if (name !in foliageChoices(project, file, entityId, metas)) return false
    return editSceneJson(project, file, AbyssusBundle.message("commandAddFoliage")) { root ->
        val tree = SceneEntityTree(root)
        val components = tree.components(entityId) ?: return@editSceneJson false
        val component = components["FoliageComponent"] as? ObjectNode
            ?: components.putObject("FoliageComponent")
        if (component["assetName"]?.textValue() == name) return@editSceneJson false
        component.put("assetName", name)
        true
    }
}

/** The same terrain-bound choices serve the tree and the Scene view toolbar. */
fun foliageGroup(project: Project, file: VirtualFile, entityId: String): DefaultActionGroup? {
    val root = SceneDocumentCache.of(project).read(file)?.root ?: return null
    if (SceneDocument(root).renderAsset(entityId)?.type != MetaType.TERRAIN.name) return null
    val metas = service<AbyssusCore>().assets.metaFiles
    val names = foliageChoices(project, file, entityId, metas)
    return DefaultActionGroup().apply {
        for (name in names) add(object : AnAction(), DumbAware {
            init { templatePresentation.setText(name, false) }
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) { setFoliage(project, file, entityId, name, metas) }
        })
        templatePresentation.isEnabled = names.isNotEmpty()
        if (names.isEmpty()) templatePresentation.description = AbyssusBundle.message("addFoliageNone")
    }
}

open class AddFoliageAction : AbyssusTreeAction<ComponentTarget>() {
    init { templatePresentation.text = AbyssusBundle.message("addFoliageTitle") }
    override fun targetOf(node: Any?): ComponentTarget? {
        val entry = (node as? DtoEntryNode)?.value ?: return null
        val json = entry.value as? com.fasterxml.jackson.databind.JsonNode ?: return null
        val target = componentTargetOf(node)?.takeIf { it.kind == null } ?: return null
        return target.takeIf { net.nevinsky.abyssus.lib.core.editor.document.sceneEntityView(entry.name, json)
            .renderAsset()?.type == MetaType.TERRAIN.name }
    }
    override fun isEnabled(e: AnActionEvent, target: ComponentTarget): Boolean {
        val group = e.project?.let { foliageGroup(it, target.file, target.entityId) }
        e.presentation.description = group?.templatePresentation?.description
        return group?.templatePresentation?.isEnabled == true
    }
    override fun perform(project: Project, target: ComponentTarget, e: AnActionEvent) {
        val group = foliageGroup(project, target.file, target.entityId) ?: return
        JBPopupFactory.getInstance().createActionGroupPopup(AbyssusBundle.message("addFoliageTitle"), group,
            e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true).showInBestPositionFor(e.dataContext)
    }
}
