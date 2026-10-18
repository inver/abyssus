/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.sceneview.SceneFileEditor
import net.nevinsky.abyssus.plugin.sceneview.openSceneView
import net.nevinsky.abyssus.plugin.ui.showActionGroup

/** The MODEL assets of the project [file] belongs to, by folder name: the only assets a path can arrange. */
internal fun pathModels(file: VirtualFile): List<RenderAsset> =
    SceneComponentEdits.renderAssets(file, service<AbyssusPluginCtx>().assets.metaFiles)
        .filter { it.type == MetaType.MODEL.name }

/** Whether a path arrangement can be added to [file] now: the scene reads as a native scene and its project has a model. */
internal fun canAddPathArrangement(file: VirtualFile, cache: SceneDocumentCache): Boolean =
    canAddAsset(file, cache) && pathModels(file).isNotEmpty()

/**
 * The project's models, by folder name, to arrange along a path drawn in [file]'s Scene view. The tree and the toolbar
 * share it; [start] is called with the model that was chosen.
 */
class AddPathArrangementGroup(
    private val project: Project,
    private val file: VirtualFile,
    private val start: (RenderAsset) -> Unit,
) : AddChildGroup<RenderAsset>(AbyssusBundle.message("addPathArrangementTitle")) {
    init {
        for (asset in pathModels(file)) addChild(asset, asset.name, literalName = true)
    }

    override fun canAdd(target: RenderAsset) = canAddAsset(file, SceneDocumentCache.of(project))
    override fun addTarget(target: RenderAsset) = start(target)
}

/** Opens the Scene view of [file] in drawing mode for the model [asset]. */
internal fun startDrawingIn(project: Project, file: VirtualFile, asset: RenderAsset) {
    openSceneView(project, file)
    fun view() = FileEditorManager.getInstance(project).getEditors(file).filterIsInstance<SceneFileEditor>()
        .firstOrNull()?.sceneView
    view()?.startDrawing(asset) ?: ApplicationManager.getApplication().invokeLater { view()?.startDrawing(asset) }
}

/** Add Path Arrangement on a scene row of the tree: the chosen model is drawn along a path in the scene's view. */
open class AddPathArrangementAction : AbyssusTreeAction<VirtualFile>() {
    init {
        templatePresentation.text = AbyssusBundle.message("addPathArrangementTitle")
    }

    override fun targetOf(node: Any?): VirtualFile? = viewableSceneFile(node)

    override fun isEnabled(e: AnActionEvent, target: VirtualFile) =
        e.project?.let { canAddPathArrangement(target, SceneDocumentCache.of(it)) } ?: false

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        showActionGroup(
            e,
            AddPathArrangementGroup(project, target) { startDrawingIn(project, target, it) },
            AbyssusBundle.message("addPathArrangementTitle")
        )
    }
}
