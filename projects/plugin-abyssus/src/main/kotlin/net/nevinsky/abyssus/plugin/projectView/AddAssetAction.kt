/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.ui.reportRejection
import net.nevinsky.abyssus.plugin.ui.showActionGroup

/**
 * The project's models and terrains to place in [file]'s scene, under Models and Terrains, by folder name. The same
 * group serves the tree and the Scene view toolbar; [position] is read when a choice is made.
 */
class AddAssetGroup(
    private val project: Project,
    private val file: VirtualFile,
    private val metaFiles: MetaFiles,
    private val position: () -> Vector3,
    private val select: (String) -> Unit = { selectCreatedEntity(project, file, it) },
    /** The mesh ids of a model asset, or null when unknown; called when the menu opens, off the UI thread. */
    private val meshes: (String) -> List<String>? = { null },
) : AddChildGroup<AssetPick>(AbyssusBundle.message("addAssetTitle")) {
    /**
     * Built each time the menu opens, because listing a model's meshes parses the model: the platform asks for the
     * children off the UI thread. A model with more than one mesh is a submenu of its meshes and the whole model; any
     * other asset is one entry.
     */
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val assets = SceneComponentEdits.renderAssets(file, metaFiles)
        val items = ArrayList<AnAction>()
        for ((type, title) in listOf(MetaType.MODEL to "addAssetModels", MetaType.TERRAIN to "addAssetTerrains")) {
            val ofType = assets.filter { it.type == type }
            if (ofType.isEmpty()) continue
            items += Separator.create(AbyssusBundle.message(title))
            for (asset in ofType) {
                val ids = if (type == MetaType.MODEL) meshes(asset.name).orEmpty() else emptyList()
                items += if (ids.size > 1) meshMenu(asset, ids) else childAction(AssetPick(asset), asset.name, literalName = true)
            }
        }
        return items.toTypedArray()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private fun meshMenu(asset: BaseMeta, ids: List<String>): AnAction = DefaultActionGroup(asset.name, true).also { menu ->
        menu.templatePresentation.setText(asset.name, false)
        menu.add(childAction(AssetPick(asset), AbyssusBundle.message("addAssetWholeModel"), literalName = true))
        menu.add(Separator.create())
        for (id in ids) menu.add(childAction(AssetPick(asset, id), id, literalName = true))
    }

    override fun canAdd(target: AssetPick) = canAddAsset(file, SceneDocumentCache.of(project))

    override fun addTarget(target: AssetPick) {
        val added = SceneComponentEdits.addAsset(
            project,
            file,
            target.asset,
            position(),
            SceneDocumentCache.of(project),
            metaFiles,
            target.meshId,
        )
        reportRejection(project, added.result)
        added.entityId?.let(select)
    }
}

/** The mesh ids of the models of the project of [file], by asset name; parses the model, so call it off the UI thread. */
internal fun projectModelMeshes(file: VirtualFile): (String) -> List<String>? = { name ->
    ProjectLayout.projectDirFor(file)?.let { dir ->
        service<AbyssusPluginCtx>().assets.loading.modelMeshes(dir, name)?.map { it.id }
    }
}

/** What Add Asset places: [asset], and for a model the one of its meshes ([meshId]) to draw, or the whole model. */
class AssetPick(val asset: BaseMeta, val meshId: String? = null)

/** Whether an asset can be added to [file] now: the scene reads as a native scene. */
internal fun canAddAsset(file: VirtualFile, cache: SceneDocumentCache): Boolean = canAddLight(file, cache)

/** Whether [file]'s project has a model or terrain to offer. */
internal fun hasRenderAssets(file: VirtualFile, metaFiles: MetaFiles): Boolean =
    SceneComponentEdits.renderAssets(file, metaFiles).isNotEmpty()

/** Add Asset on a scene row of the tree: the chosen asset is placed at the scene's origin. */
open class AddAssetAction : AbyssusTreeAction<VirtualFile>() {
    init {
        templatePresentation.text = AbyssusBundle.message("addAssetTitle")
    }

    override fun targetOf(node: Any?): VirtualFile? = viewableSceneFile(node)

    override fun isEnabled(e: AnActionEvent, target: VirtualFile) =
        e.project?.let {
            canAddAsset(target, SceneDocumentCache.of(it)) && hasRenderAssets(
                target,
                service<AbyssusPluginCtx>().assets.metaFiles
            )
        } ?: false

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        showActionGroup(
            e,
            AddAssetGroup(
                project, target, service<AbyssusPluginCtx>().assets.metaFiles, { Vector3(0f, 0f, 0f) },
                meshes = projectModelMeshes(target),
            ),
            AbyssusBundle.message("addAssetTitle")
        )
    }
}
