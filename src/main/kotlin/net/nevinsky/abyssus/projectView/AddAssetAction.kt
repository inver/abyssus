/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.projectView

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.dto.SceneDocumentCache
import net.nevinsky.abyssus.editor.content.Vec3

/**
 * The project's models and terrains to place in [file]'s scene, under Models and Terrains, by folder name. The same
 * group serves the tree and the Scene view toolbar; [position] is read when a choice is made.
 */
class AddAssetGroup(
    project: Project,
    file: VirtualFile,
    position: () -> Vec3,
    select: (String) -> Unit = { selectCreatedEntity(project, file, it) },
) : DefaultActionGroup(AbyssusBundle.message("addAssetTitle"), true), DumbAware {
    init {
        val assets = SceneComponentEdits.renderAssets(file, service<AbyssusCore>().assets.metaFiles)
        for ((type, title) in listOf(MetaType.MODEL to "addAssetModels", MetaType.TERRAIN to "addAssetTerrains")) {
            val ofType = assets.filter { it.type == type.name }
            if (ofType.isEmpty()) continue
            add(Separator.create(AbyssusBundle.message(title)))
            for (asset in ofType) add(object : AnAction(), DumbAware {
                init {
                    templatePresentation.setText(asset.name, false) // folder names are shown as they are: `_` is no mnemonic
                }

                override fun getActionUpdateThread() = ActionUpdateThread.BGT
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = canAddAsset(file, SceneDocumentCache.of(project))
                }

                override fun actionPerformed(e: AnActionEvent) {
                    val added = SceneComponentEdits.addAsset(project, file, asset, position(), SceneDocumentCache.of(project), service<AbyssusCore>().assets.metaFiles)
                    reportRejection(project, added.result)
                    added.entityId?.let(select)
                }
            })
        }
    }
}

/** Whether an asset can be added to [file] now: the scene reads as a native scene. */
internal fun canAddAsset(file: VirtualFile, cache: SceneDocumentCache): Boolean = canAddLight(file, cache)

/** Whether [file]'s project has a model or terrain to offer. */
internal fun hasRenderAssets(file: VirtualFile): Boolean =
    SceneComponentEdits.renderAssets(file, service<AbyssusCore>().assets.metaFiles).isNotEmpty()

/** Add Asset on a scene row of the tree: the chosen asset is placed at the scene's origin. */
open class AddAssetAction : AbyssusTreeAction<VirtualFile>() {
    init { templatePresentation.text = AbyssusBundle.message("addAssetTitle") }

    override fun targetOf(node: Any?): VirtualFile? = viewableSceneFile(node)

    override fun isEnabled(e: AnActionEvent, target: VirtualFile) =
        e.project?.let { canAddAsset(target, SceneDocumentCache.of(it)) && hasRenderAssets(target) } ?: false

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        JBPopupFactory.getInstance().createActionGroupPopup(
            AbyssusBundle.message("addAssetTitle"),
            AddAssetGroup(project, target, { Vec3(0f, 0f, 0f) }),
            e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
        ).showInBestPositionFor(e.dataContext)
    }
}
