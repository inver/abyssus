/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.textOf

private val LOG = Logger.getInstance("net.nevinsky.abyssus.projectView.AssetSelection")

/** The project file (`.abss`) a selected Assets node belongs to, or null for any other node. */
fun assetsNodeProjectFile(node: Any?): VirtualFile? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    if (entry.name != "assets" || entry.value !is List<*>) return null
    return entry.source?.takeIf { it.isValid && it.extension == ProjectLayout.PROJECT_EXTENSION &&
        net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation {
            net.nevinsky.abyssus.lib.core.editor.document.AbyssusDocumentFormat().validate(
                net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(textOf(it)),
                net.nevinsky.abyssus.lib.core.editor.document.DocumentKind.PROJECT,
            ) == null
        }.getOrDefault(false) }
}

/**
 * What a tree visitor should do with the node at the end of [chain] (user objects from the view root down) when looking
 * for the asset row [assetName] under the Assets node of the project file [abss].
 */
fun assetVisitAction(abss: VirtualFile, assetName: String, chain: List<Any?>): TreeVisitor.Action {
    val nodes = chain.filterNotNull()
    val projectIndex = nodes.indexOfFirst { it is AbyssusAssetNode && it.virtualFile == abss }
    if (projectIndex < 0) {
        return when (nodes.lastOrNull()) {
            null, is AbyssusRootNode -> TreeVisitor.Action.CONTINUE
            is AbyssusAssetNode -> TreeVisitor.Action.SKIP_CHILDREN
            is DtoEntryNode -> TreeVisitor.Action.SKIP_CHILDREN
            else -> TreeVisitor.Action.CONTINUE // the platform's root of the tree
        }
    }
    val below = nodes.drop(projectIndex + 1).map { it as? DtoEntryNode ?: return TreeVisitor.Action.SKIP_CHILDREN }
    return when {
        below.isEmpty() -> TreeVisitor.Action.CONTINUE
        below[0].value.name != "assets" -> TreeVisitor.Action.SKIP_CHILDREN
        below.size == 1 -> TreeVisitor.Action.CONTINUE
        below.size == 2 && (below[1].value.value as? Asset<*>)?.name == assetName -> TreeVisitor.Action.INTERRUPT
        else -> TreeVisitor.Action.SKIP_CHILDREN
    }
}

/**
 * Refreshes the Abyssus view and selects the row of asset [assetName] under [abss] in it, which also shows its
 * properties. Never throws; call on the EDT.
 */
fun selectAssetInAbyssusView(project: Project, abss: VirtualFile, assetName: String) {
    try {
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)?.show()
        val view = ProjectView.getInstance(project)
        view.changeView(AbyssusProjectViewPane.ID)
        val pane = view.getProjectViewPaneById(AbyssusProjectViewPane.ID) ?: return
        pane.updateFromRoot(true)
        val tree = pane.tree ?: return
        TreeUtil.promiseSelect(tree, TreeVisitor { path -> assetVisitAction(abss, assetName, path.path.map(TreeUtil::getUserObject)) })
    } catch (e: Throwable) {
        LOG.warn("Could not select asset $assetName in the Abyssus view", e)
    }
}
