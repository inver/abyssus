/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.hierarchy.HierarchyWarning

/**
 * The parent links of a scene's content: [parents] (child to parent, only links that were followed), each child's
 * [parentFrames] (its parent's world matrix times the node's rest transform), each entity's [worlds] matrix, the
 * stored [locals] of entities with a `PositionComponent`, the [node] each child is bound to, the entities disabled by
 * an
 * ancestor ([disabledByAncestor]) and the [warnings] of the resolve. Equal when the values are equal, matrices
 * included.
 */
class ContentHierarchy(
    val parents: Map<Long, Long> = emptyMap(),
    val parentFrames: Map<Long, Matrix4> = emptyMap(),
    val worlds: Map<Long, Matrix4> = emptyMap(),
    val locals: Map<Long, PlacementTransform> = emptyMap(),
    val nodes: Map<Long, String> = emptyMap(),
    val disabledByAncestor: Set<Long> = emptySet(),
    val warnings: List<HierarchyWarning> = emptyList(),
) {
    private val children: Map<Long, List<Long>> by lazy(LazyThreadSafetyMode.NONE) {
        parents.entries.groupBy({ it.value }, { it.key }).mapValues { (_, ids) -> ids.sorted() }
    }

    /** Whether [id] is the child of another entity (a followed link). */
    fun isChild(id: Long): Boolean = id in parents

    /** The children of [id], in id order. */
    fun childrenOf(id: Long): List<Long> = children[id].orEmpty()

    /** Every descendant of [id], depth first, children in id order. */
    fun descendantsOf(id: Long): List<Long> =
        buildList {
            val stack = ArrayDeque(childrenOf(id).asReversed())
            while (stack.isNotEmpty()) {
                val next = stack.removeLast()
                add(next)
                childrenOf(next).asReversed().forEach(stack::addLast)
            }
        }

    /** The parent frame of [id] (identity for a root), a copy. */
    fun parentFrameOf(id: Long): Matrix4 = parentFrames[id]?.let(::Matrix4) ?: Matrix4()

    /** The world matrix of [id] (identity when unknown), a copy. */
    fun worldOf(id: Long): Matrix4 = worlds[id]?.let(::Matrix4) ?: Matrix4()

    override fun equals(other: Any?): Boolean =
        other is ContentHierarchy &&
            parents == other.parents &&
            sameMatrices(parentFrames, other.parentFrames) &&
            sameMatrices(worlds, other.worlds) &&
            locals == other.locals &&
            nodes == other.nodes &&
            disabledByAncestor == other.disabledByAncestor &&
            warnings == other.warnings

    override fun hashCode(): Int = parents.hashCode() * 31 + locals.hashCode()

    override fun toString(): String = "ContentHierarchy(parents=$parents, nodes=$nodes, warnings=$warnings)"

    private fun sameMatrices(a: Map<Long, Matrix4>, b: Map<Long, Matrix4>): Boolean =
        a.keys == b.keys && a.all { (k, m) -> m.`val`.contentEquals(b.getValue(k).`val`) }
}

/**
 * A dummy entity (a `DUMMY`, a named attach point) as the view draws its marker: [transform] is its world pose,
 * [parentId] its followed parent (null when it is a root).
 */
data class DummyPlacement(
    val entityId: Long,
    val name: String,
    val transform: PlacementTransform,
    val parentId: Long?,
)
