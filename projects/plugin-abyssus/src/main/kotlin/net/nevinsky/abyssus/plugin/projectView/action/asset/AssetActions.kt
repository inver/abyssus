/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView.action.asset

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.projectView.*

private val LOG = Logger.getInstance("net.nevinsky.abyssus.projectView.AssetSelection")


class AssetDropdownAction : DropdownAction {
    override val actionIds = listOf("Abyssus.Menu.ImportModel", "Abyssus.Menu.New")

    override fun appliesTo(node: Any?) = assetsNodeProjectFile(node) != null
}

/** The project file (`.abss`) a selected Assets node belongs to, or null for any other node. */
fun assetsNodeProjectFile(node: Any?): VirtualFile? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    if ((entry.value as? FolderRow)?.kind != FolderKind.ASSETS) {
        return null
    }
    return entry.source?.takeIf {
        it.isValid && it.extension == ProjectLayout.PROJECT_EXTENSION &&
                runCatchingKeepingCancellation {
                    AbyssusDocumentFormat().validate(
                        SceneJson().parse(textOf(it)),
                        DocumentKind.PROJECT,
                    ) == null
                }.getOrDefault(false)
    }
}

/**
 * What a tree visitor should do with the node at the end of [chain] (user objects from the view root down) when looking
 * for the asset row [assetName] under the UI or Assets node of the project file [abss].
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
        // UI assets sit under `ui`; every other asset under `assets` and then its type group
        below[0].value.value !is FolderRow -> TreeVisitor.Action.SKIP_CHILDREN
        (below.last().value.value as? Asset<*>)?.name == assetName -> TreeVisitor.Action.INTERRUPT
        below.last().value.value is Asset<*> -> TreeVisitor.Action.SKIP_CHILDREN
        below.size <= 2 -> TreeVisitor.Action.CONTINUE
        else -> TreeVisitor.Action.SKIP_CHILDREN
    }
}

/**
 * Refreshes the Abyssus view and selects the row of asset [assetName] under [abss] in it, which also shows its
 * properties. Never throws; call on the EDT.
 */
fun selectAssetInAbyssusView(project: Project, abss: VirtualFile, assetName: String) {
    try {
        selectInAbyssusView(
            project,
            { path -> assetVisitAction(abss, assetName, path.path.map(TreeUtil::getUserObject)) },
            refresh = true
        )
    } catch (e: Throwable) {
        LOG.warn("Could not select asset $assetName in the Abyssus view", e)
    }
}
