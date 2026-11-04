/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCollider
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderShape
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.editor.document.colliderNode
import net.nevinsky.abyssus.lib.core.editor.util.JsonValues
import net.nevinsky.abyssus.lib.core.editor.util.valuesOf
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/**
 * Turns an edited [FoliageCollider] back into the `collider` member a layer stores. It starts from the member the layer
 * already has, so a nested member this program does not know, an inactive shape's sizes and key order stay, and it sets
 * only the members the shape uses: the shape, the active sizes and an `offset` that is not zero (or that the member
 * already had).
 */
class FoliageColliderEdits(private val json: JsonProcessor) {

    /**
     * True when [after] differs from [before] only in the layers' colliders: nothing a bake or a mask is made from
     * changed.
     */
    fun onlyColliders(before: FoliageMeta, after: FoliageMeta): Boolean {
        if (before == after) {
            return false
        }
        if (before.copy(layers = emptyList()) != after.copy(layers = emptyList())) {
            return false
        }
        if (before.layers.size != after.layers.size) {
            return false
        }
        return before.layers.zip(after.layers).all { (a, b) -> a.copy(collider = null) == b.copy(collider = null) }
    }

    /**
     * [layer] with its `collider` member set for [collider] on top of the one it has; no collider removes the member.
     */
    fun patch(layer: FoliageLayerMeta, collider: FoliageCollider?): FoliageLayerMeta {
        if (collider == null) {
            return layer.copy(collider = null)
        }
        val stored: JsonValues? = layer.collider?.let { valuesOf(json.parse(it.toString(), Map::class.java)) }
        val out = LinkedHashMap<String, Any?>(stored.orEmpty())
        out["shape"] = collider.shape.name
        when (collider.shape) {
            FoliageColliderShape.BOX ->
                out.vector("halfExtents", collider.halfX, collider.halfY, collider.halfZ, always = true)
            FoliageColliderShape.SPHERE -> out["radius"] = collider.radius
            FoliageColliderShape.CAPSULE -> {
                out["radius"] = collider.radius
                out["halfHeight"] = collider.halfHeight
            }
        }
        out.vector("offset", collider.offsetX, collider.offsetY, collider.offsetZ, always = false)
        return layer.copy(collider = colliderNode(json, out))
    }

    private fun MutableMap<String, Any?>.vector(key: String, x: Float, y: Float, z: Float, always: Boolean) {
        val stored = valuesOf(get(key))
        if (stored == null && !always && x == 0f && y == 0f && z == 0f) {
            return
        }
        val target = LinkedHashMap<String, Any?>(stored.orEmpty())
        target["x"] = x
        target["y"] = y
        target["z"] = z
        this[key] = target
    }
}
