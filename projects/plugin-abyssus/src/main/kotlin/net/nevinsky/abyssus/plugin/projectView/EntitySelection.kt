/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementOwnerOf
import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.util.rawAt
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache

private val LOG = Logger.getInstance("net.nevinsky.abyssus.projectView.EntitySelection")

/**
 * Where an entity sits under its scene's node: `ecs` / its [ancestors] (root first) / `<id>`, as a child row is listed
 * under its parent's row (the `entities` level is not shown).
 */
private fun entityPath(entityId: Long, ancestors: List<Long>) =
    listOf("ecs") + ancestors.map(Long::toString) + entityId.toString()

/**
 * What a tree visitor should do with the node at the end of [chain] (the user objects from the view root down) when
 * looking for the row of entity [entityId] of [sceneFile], whose parent rows are [ancestors] (root first; empty for a
 * root entity). The scene is found either as a standalone `.scene` asset or as an entry of its project's `scenes` list.
 */
fun entityVisitAction(
    sceneFile: VirtualFile,
    entityId: Long,
    chain: List<Any?>,
    ancestors: List<Long> = emptyList(),
): TreeVisitor.Action = rowVisitAction(sceneFile, entityPath(entityId, ancestors), chain)

/** The parent rows of entity [entityId] in [scene]'s tree, root first; empty for a root entity. */
fun treeAncestors(scene: net.nevinsky.abyssus.lib.core.dto.SceneDto, entityId: Long): List<Long> {
    val parents = treeParents(scene)
    val chain = ArrayList<Long>()
    var current = parents[entityId]
    while (current != null && current !in chain) {
        chain += current
        current = parents[current]
    }
    return chain.asReversed()
}

/**
 * What a tree visitor should do with the node at the end of [chain] when looking for the row [wanted] (row names below
 * the scene's node) of [sceneFile]. Shared by entity and water rows.
 */
fun rowVisitAction(sceneFile: VirtualFile, wanted: List<String>, chain: List<Any?>): TreeVisitor.Action {
    val nodes = chain.filterNotNull()
    val sceneIndex = nodes.indexOfFirst { isSceneRoot(it, sceneFile) }
    if (sceneIndex >= 0) {
        val below =
            nodes.drop(sceneIndex + 1).map {
                (it as? DtoEntryNode)?.value?.name ?: return TreeVisitor.Action.SKIP_CHILDREN
            }
        return when {
            below == wanted -> TreeVisitor.Action.INTERRUPT
            below.size < wanted.size && wanted.subList(0, below.size) == below -> TreeVisitor.Action.CONTINUE
            else -> TreeVisitor.Action.SKIP_CHILDREN
        }
    }
    // not inside the scene yet: only its project file and the `scenes` list lead there
    return when (val last = nodes.lastOrNull()) {
        null,
        is AbyssusRootNode -> TreeVisitor.Action.CONTINUE
        is AbyssusAssetNode ->
            if (last.virtualFile == ProjectLayout.abssFor(sceneFile)) {
                TreeVisitor.Action.CONTINUE
            } else {
                TreeVisitor.Action.SKIP_CHILDREN
            }
        is DtoEntryNode ->
            if (last.value.name == "scenes" && nodes.getOrNull(nodes.size - 2) is AbyssusAssetNode) {
                TreeVisitor.Action.CONTINUE
            } else {
                TreeVisitor.Action.SKIP_CHILDREN
            }
        else -> TreeVisitor.Action.CONTINUE // the platform's root of the tree
    }
}

private fun isSceneRoot(node: Any, sceneFile: VirtualFile): Boolean =
    when (node) {
        is AbyssusAssetNode -> node.virtualFile == sceneFile
        is DtoEntryNode -> sceneFileOf(node.value) == sceneFile
        else -> false
    }

/**
 * Shows the Abyssus view and selects the row of entity [entityId] of [sceneFile] in it, expanding the tree as needed.
 * Does nothing when the row does not exist; never throws. Call on the EDT.
 */
fun selectEntityInAbyssusView(project: Project, sceneFile: VirtualFile, entityId: Long) {
    try {
        // a linked copy of a path arrangement has no row of its own: its arrangement's row stands for it
        val scene = SceneDocumentCache.of(project).read(sceneFile)?.scene
        val rowId = scene?.let { arrangementOwnerOf(it, entityId) } ?: entityId
        // a child's row is under its parent's: the walk expands each ancestor on the way (OpenSpec
        // abyssus-project-view)
        val ancestors = scene?.let { treeAncestors(it, rowId) }.orEmpty()
        val visitor = TreeVisitor { path ->
            entityVisitAction(sceneFile, rowId, path.path.map(TreeUtil::getUserObject), ancestors)
        }
        selectInAbyssusView(project, visitor)
    } catch (e: Throwable) {
        LOG.warn("Could not select entity $entityId in the Abyssus view", e)
    }
}

/**
 * Shows the Abyssus view and selects the row of part [node] of model entity [entityId] of [sceneFile] in it, expanding
 * the tree down to it. Does nothing when the parts are not read yet or have no such part; never throws. EDT.
 */
fun selectPartInAbyssusView(project: Project, sceneFile: VirtualFile, entityId: Long, node: String) {
    try {
        val scene = SceneDocumentCache.of(project).read(sceneFile)?.scene ?: return
        val render = scene.entity(entityId)
            ?.component(net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent::class.java) ?: return
        val dir = ProjectLayout.projectDirFor(sceneFile) ?: return
        val key = net.nevinsky.abyssus.lib.core.assets.ModelKey(
            render.assetName,
            render.meshId?.takeIf(String::isNotEmpty),
        ).encode()
        val chain = TreeModelParts.of(project).cached(dir, key)?.info?.pathTo(node) ?: return
        val ancestors = treeAncestors(scene, entityId)
        val wanted = entityPath(entityId, ancestors) + PARTS_ROW + chain
        val visitor = TreeVisitor { path -> rowVisitAction(sceneFile, wanted, path.path.map(TreeUtil::getUserObject)) }
        selectInAbyssusView(project, visitor)
    } catch (e: Throwable) {
        LOG.warn("Could not select part $node of entity $entityId in the Abyssus view", e)
    }
}

// G7: selectWaterInAbyssusView (selecting a water surface in the tree) is disabled.
