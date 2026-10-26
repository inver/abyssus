/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.MetaType
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.ui.reportRejection
import net.nevinsky.abyssus.plugin.dto.MetaFiles
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
) : AddChildGroup<net.nevinsky.abyssus.lib.core.editor.content.RenderAsset>(AbyssusBundle.message("addAssetTitle")) {
    init {
        val assets = SceneComponentEdits.renderAssets(file, metaFiles)
        for ((type, title) in listOf(MetaType.MODEL to "addAssetModels", MetaType.TERRAIN to "addAssetTerrains")) {
            val ofType = assets.filter { it.type == type.name }
            if (ofType.isEmpty()) continue
            add(Separator.create(AbyssusBundle.message(title)))
            for (asset in ofType) addChild(asset, asset.name, literalName = true)
        }
    }

    override fun canAdd(target: net.nevinsky.abyssus.lib.core.editor.content.RenderAsset) =
        canAddAsset(file, SceneDocumentCache.of(project))

    override fun addTarget(target: net.nevinsky.abyssus.lib.core.editor.content.RenderAsset) {
        val added = SceneComponentEdits.addAsset(
            project,
            file,
            target,
            position(),
            SceneDocumentCache.of(project),
            metaFiles,
        )
        reportRejection(project, added.result)
        added.entityId?.let(select)
    }
}

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
        e.project?.let { canAddAsset(target, SceneDocumentCache.of(it)) && hasRenderAssets(target, service<AbyssusPluginCtx>().assets.metaFiles) } ?: false

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        showActionGroup(e, AddAssetGroup(project, target, service<AbyssusPluginCtx>().assets.metaFiles, { Vector3(0f, 0f, 0f) }), AbyssusBundle.message("addAssetTitle"))
    }
}
