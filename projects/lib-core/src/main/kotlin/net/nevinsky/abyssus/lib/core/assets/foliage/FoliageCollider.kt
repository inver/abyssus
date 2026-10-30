/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import com.fasterxml.jackson.databind.JsonNode

/** The shapes an OBJECT foliage layer's collider can take. */
enum class FoliageColliderShape { BOX, SPHERE, CAPSULE }

/**
 * A layer's collider as its `collider` member declares it, in model units and relative to each copy's origin. The
 * defaults are those of the physics `ColliderComponent`, so a member left out reads the same in a scene and in a layer.
 * Only the members of the active [shape] are meaningful: a BOX uses [halfX], [halfY] and [halfZ], a SPHERE its
 * [radius], a CAPSULE its [radius] and [halfHeight] (along the copy's up axis). The class names no physics type.
 */
data class FoliageCollider(
    val shape: FoliageColliderShape = FoliageColliderShape.BOX,
    val halfX: Float = 0.5f,
    val halfY: Float = 0.5f,
    val halfZ: Float = 0.5f,
    val radius: Float = 0.5f,
    val halfHeight: Float = 0.5f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
)

/** What a layer's `collider` member means. */
sealed interface FoliageColliderRead {
    /** No member (or a null one): the layer does not collide. */
    data object Absent : FoliageColliderRead

    /** A collider that can be built. */
    data class Valid(val collider: FoliageCollider) : FoliageColliderRead

    /** A member that is present but not usable: an unknown shape, a malformed payload or an invalid active size. */
    data class Invalid(val problem: FoliageColliderProblem, val reason: String) : FoliageColliderRead
}

/** Why a collider member cannot be built, for callers that show their own localized text. */
enum class FoliageColliderProblem {
    /** The member is not an object, or one of its modeled members has the wrong type. */
    MALFORMED,

    /** `shape` is not BOX, SPHERE or CAPSULE. */
    UNKNOWN_SHAPE,

    /** An active size is not finite and positive. */
    SIZE,

    /** An offset component is not finite. */
    OFFSET,
}

/**
 * Decodes a layer's raw `collider` member. Absent is told apart from invalid, so a bad collider disables only that
 * layer's collision and never its rendered copies, and the raw node stays with the layer so a nested member this
 * reader does not know survives an edit. Members of an inactive shape are kept but not checked. Stateless.
 */
class FoliageColliderReader {

    fun read(node: JsonNode?): FoliageColliderRead {
        if (node == null || node.isNull || node.isMissingNode) return FoliageColliderRead.Absent
        if (!node.isObject) return invalid(FoliageColliderProblem.MALFORMED, "the collider is not an object")
        val shape = when (val raw = node.get("shape")) {
            null, is com.fasterxml.jackson.databind.node.NullNode -> FoliageColliderShape.BOX
            else -> raw.takeIf { it.isTextual }?.asText()?.let { name -> FoliageColliderShape.entries.firstOrNull { it.name == name } }
                ?: return invalid(FoliageColliderProblem.UNKNOWN_SHAPE, "the collider shape $raw is not BOX, SPHERE or CAPSULE")
        }
        val half = vector(node.get("halfExtents"), "halfExtents", 0.5f) ?: return invalid(FoliageColliderProblem.MALFORMED, "halfExtents must hold numbers x, y and z")
        val offset = vector(node.get("offset"), "offset", 0f) ?: return invalid(FoliageColliderProblem.MALFORMED, "offset must hold numbers x, y and z")
        val radius = number(node, "radius", 0.5f) ?: return invalid(FoliageColliderProblem.MALFORMED, "radius must be a number")
        val halfHeight = number(node, "halfHeight", 0.5f) ?: return invalid(FoliageColliderProblem.MALFORMED, "halfHeight must be a number")
        val collider = FoliageCollider(shape, half[0], half[1], half[2], radius, halfHeight, offset[0], offset[1], offset[2])
        return validate(collider)?.let { invalid(it.first, it.second) } ?: FoliageColliderRead.Valid(collider)
    }

    /** What keeps [collider] from being built (an active size that is not finite and positive, a non-finite offset), or null. */
    fun validate(collider: FoliageCollider): Pair<FoliageColliderProblem, String>? {
        fun bad(v: Float) = !(v.isFinite() && v > 0f)
        if (!collider.offsetX.isFinite() || !collider.offsetY.isFinite() || !collider.offsetZ.isFinite()) {
            return FoliageColliderProblem.OFFSET to "the collider offset must be finite"
        }
        val size = when (collider.shape) {
            FoliageColliderShape.BOX ->
                if (bad(collider.halfX) || bad(collider.halfY) || bad(collider.halfZ)) "the box half extents must be finite and positive" else null
            FoliageColliderShape.SPHERE -> if (bad(collider.radius)) "the sphere radius must be finite and positive" else null
            FoliageColliderShape.CAPSULE ->
                if (bad(collider.radius) || bad(collider.halfHeight)) "the capsule radius and half height must be finite and positive" else null
        }
        return size?.let { FoliageColliderProblem.SIZE to it }
    }

    private fun invalid(problem: FoliageColliderProblem, reason: String) = FoliageColliderRead.Invalid(problem, reason)

    private fun number(parent: JsonNode, key: String, default: Float): Float? {
        val value = parent.get(key)
        if (value == null || value.isNull) return default
        return if (value.isNumber) value.floatValue() else null
    }

    /** The x, y and z of [node], each defaulting to [default]; null when [node] or a member is not what it should be. */
    private fun vector(node: JsonNode?, @Suppress("UNUSED_PARAMETER") name: String, default: Float): FloatArray? {
        if (node == null || node.isNull) return floatArrayOf(default, default, default)
        if (!node.isObject) return null
        val out = FloatArray(3)
        for ((i, key) in listOf("x", "y", "z").withIndex()) out[i] = number(node, key, default) ?: return null
        return out
    }
}
