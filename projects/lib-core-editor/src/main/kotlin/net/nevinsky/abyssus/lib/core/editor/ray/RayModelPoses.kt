/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.editor.ray

import net.nevinsky.abyssus.lib.gdx.editor.scene.ModelEntity
import net.nevinsky.abyssus.lib.gdx.node.Node

/**
 * Copies each animated or skinned entity's displayed pose from its live `ModelInstance`: every node's global transform
 * and every skinned part's bone matrices, exactly as the raster pass drew them this frame. Render thread only, after
 * animations were advanced. Nothing here recalculates transforms, so raster and ray frames cannot disagree.
 *
 * A pose's revision advances only when its content changed, which lets the scene diff tell a still model from a moving
 * one. Entities that were not captured in a call forget their revision.
 */
class RayModelPoses {
    private class Last(val pose: RayModelPose, val nodes: Map<String, FloatArray>, val bones: Map<String, List<FloatArray>>)

    private var previous = emptyMap<String, Last>()

    fun capture(entities: Collection<ModelEntity>): Map<String, RayModelPose> {
        val captured = LinkedHashMap<String, Last>()
        for (entity in entities) {
            val roots = entity.instance.nodes
            if (entity.animation == null && roots.none(::skinned)) continue
            val nodes = LinkedHashMap<String, FloatArray>()
            val bones = LinkedHashMap<String, List<FloatArray>>()
            roots.forEach { collect(it, nodes, bones) }
            val id = entity.placement.entityId
            val before = previous[id]
            val unchanged = before != null && same(before.nodes, nodes) && sameBones(before.bones, bones)
            val pose = if (unchanged) before!!.pose else RayModelPose((before?.pose?.revision ?: 0L) + 1, nodes, bones)
            captured[id] = if (unchanged) before!! else Last(pose, nodes, bones)
        }
        previous = captured
        return captured.mapValues { it.value.pose }
    }

    private fun skinned(node: Node): Boolean =
        node.parts.any { it.bones != null } || (0 until node.childCount).any { skinned(node.getChild(it)!!) }

    private fun collect(node: Node, nodes: MutableMap<String, FloatArray>, bones: MutableMap<String, List<FloatArray>>) {
        node.id?.let { nodes[it] = node.globalTransform.`val`.copyOf() }
        for (part in node.parts) {
            val skin = part.bones ?: continue
            bones["${node.id}/${part.meshPart?.id}"] = skin.map { it.`val`.copyOf() }
        }
        for (i in 0 until node.childCount) collect(node.getChild(i)!!, nodes, bones)
    }

    private fun same(a: Map<String, FloatArray>, b: Map<String, FloatArray>) =
        a.keys == b.keys && a.all { (key, value) -> value.contentEquals(b.getValue(key)) }

    private fun sameBones(a: Map<String, List<FloatArray>>, b: Map<String, List<FloatArray>>) =
        a.keys == b.keys && a.all { (key, list) ->
            val other = b.getValue(key)
            list.size == other.size && list.indices.all { list[it].contentEquals(other[it]) }
        }
}
