/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.plugin.dto.ProjectLayout

private val LOG = Logger.getInstance("net.nevinsky.abyssus.projectView.EntitySelection")

/** Where an entity sits under its scene's node: `ecs` / `<id>` (the `entities` level is not shown). */
private fun entityPath(entityId: String) = net.nevinsky.abyssus.lib.gdx.editor.document.sceneEntityTreePath(entityId)

/**
 * What a tree visitor should do with the node at the end of [chain] (the user objects from the view root down) when
 * looking for the row of entity [entityId] of [sceneFile]. The scene is found either as a standalone `.scene` asset
 * or as an entry of its project's `scenes` list.
 */
fun entityVisitAction(sceneFile: VirtualFile, entityId: String, chain: List<Any?>): TreeVisitor.Action {
    val nodes = chain.filterNotNull()
    val sceneIndex = nodes.indexOfFirst { isSceneRoot(it, sceneFile) }
    if (sceneIndex >= 0) {
        val below = nodes.drop(sceneIndex + 1).map { (it as? DtoEntryNode)?.value?.name ?: return TreeVisitor.Action.SKIP_CHILDREN }
        val wanted = entityPath(entityId)
        return when {
            below == wanted -> TreeVisitor.Action.INTERRUPT
            below.size < wanted.size && wanted.subList(0, below.size) == below -> TreeVisitor.Action.CONTINUE
            else -> TreeVisitor.Action.SKIP_CHILDREN
        }
    }
    // not inside the scene yet: only its project file and the `scenes` list lead there
    return when (val last = nodes.lastOrNull()) {
        null, is AbyssusRootNode -> TreeVisitor.Action.CONTINUE
        is AbyssusAssetNode -> if (last.virtualFile == ProjectLayout.abssFor(sceneFile)) TreeVisitor.Action.CONTINUE else TreeVisitor.Action.SKIP_CHILDREN
        is DtoEntryNode ->
            if (last.value.name == "scenes" && nodes.getOrNull(nodes.size - 2) is AbyssusAssetNode) TreeVisitor.Action.CONTINUE
            else TreeVisitor.Action.SKIP_CHILDREN
        else -> TreeVisitor.Action.CONTINUE // the platform's root of the tree
    }
}

private fun isSceneRoot(node: Any, sceneFile: VirtualFile): Boolean = when (node) {
    is AbyssusAssetNode -> node.virtualFile == sceneFile
    is DtoEntryNode -> sceneFileOf(node.value) == sceneFile
    else -> false
}

/**
 * Shows the Abyssus view and selects the row of entity [entityId] of [sceneFile] in it, expanding the tree as needed.
 * Does nothing when the row does not exist; never throws. Call on the EDT.
 */
fun selectEntityInAbyssusView(project: Project, sceneFile: VirtualFile, entityId: String) {
    try {
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)?.show()
        val view = ProjectView.getInstance(project)
        view.changeView(AbyssusProjectViewPane.ID)
        val tree = view.getProjectViewPaneById(AbyssusProjectViewPane.ID)?.tree ?: return
        val visitor = TreeVisitor { path -> entityVisitAction(sceneFile, entityId, path.path.map(TreeUtil::getUserObject)) }
        TreeUtil.promiseSelect(tree, visitor)
    } catch (e: Throwable) {
        LOG.warn("Could not select entity $entityId in the Abyssus view", e)
    }
}
