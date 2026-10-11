/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCollider
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderShape
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta

/**
 * Turns an edited [FoliageCollider] back into the `collider` member a layer stores. It starts from the member the
 * layer already has, so a nested member this program does not know, an inactive shape's sizes and key order stay,
 * and it sets only the members the shape uses: the shape, the active sizes and an `offset` that is not zero (or that
 * the member already had). Stateless.
 */
class FoliageColliderEdits {

    /** True when [after] differs from [before] only in the layers' colliders: nothing a bake or a mask is made from changed. */
    fun onlyColliders(before: FoliageMeta, after: FoliageMeta): Boolean {
        if (before == after) return false
        if (before.copy(layers = emptyList()) != after.copy(layers = emptyList())) return false
        if (before.layers.size != after.layers.size) return false
        return before.layers.zip(after.layers).all { (a, b) -> a.copy(collider = null) == b.copy(collider = null) }
    }

    /** The member for [collider] on top of [stored]; null for no collider, which removes the member. */
    fun patch(stored: JsonNode?, collider: FoliageCollider?): JsonNode? {
        if (collider == null) return null
        val out = (stored as? ObjectNode)?.deepCopy() ?: JsonNodeFactory.instance.objectNode()
        out.put("shape", collider.shape.name)
        when (collider.shape) {
            FoliageColliderShape.BOX -> out.vector("halfExtents", collider.halfX, collider.halfY, collider.halfZ, always = true)
            FoliageColliderShape.SPHERE -> out.put("radius", collider.radius)
            FoliageColliderShape.CAPSULE -> {
                out.put("radius", collider.radius)
                out.put("halfHeight", collider.halfHeight)
            }
        }
        out.vector("offset", collider.offsetX, collider.offsetY, collider.offsetZ, always = false)
        return out
    }

    private fun ObjectNode.vector(key: String, x: Float, y: Float, z: Float, always: Boolean) {
        val stored = get(key) as? ObjectNode
        if (stored == null && !always && x == 0f && y == 0f && z == 0f) return
        val target = stored ?: putObject(key)
        target.put("x", x)
        target.put("y", y)
        target.put("z", z)
    }
}
