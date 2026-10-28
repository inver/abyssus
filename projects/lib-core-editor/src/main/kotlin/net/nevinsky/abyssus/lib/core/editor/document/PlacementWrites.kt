/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.FloatNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * The one rule for writing a position or rotation into a `PositionComponent` object, used by the gizmo, arrangements and
 * paste, so they share one set of rules. A missing vector or quaternion object is created with every field, or with only
 * the fields that differ from their defaults when [omitDefaults] is set (the component codec's omission); an existing
 * one gets only the fields that differ from what it holds, an absent field holding its default. Numbers are written
 * as floats.
 */
class PlacementWrites(private val omitDefaults: Boolean = false) {
    private class Field(val key: String, val value: Float, val default: Float)

    /** Writes [v] as the object [name] of [parent]; false when nothing needed to change. */
    fun setVector(parent: ObjectNode, name: String, v: Vector3): Boolean =
        setFields(parent, name, listOf(Field("x", v.x, 0f), Field("y", v.y, 0f), Field("z", v.z, 0f)))

    /** Writes [q] as the object [name] of [parent]; false when nothing needed to change. */
    fun setQuaternion(parent: ObjectNode, name: String, q: Quaternion): Boolean =
        setFields(parent, name, listOf(Field("x", q.x, 0f), Field("y", q.y, 0f), Field("z", q.z, 0f), Field("w", q.w, 1f)))

    /** The `PositionComponent` object of [components], added when missing; null when it holds something else. */
    fun placementOf(components: ObjectNode): ObjectNode? {
        val existing = components.get("PositionComponent")
        return if (existing == null || existing.isNull) components.putObject("PositionComponent") else existing as? ObjectNode
    }

    private fun setFields(parent: ObjectNode, name: String, fields: List<Field>): Boolean {
        val existing = parent.get(name)
        if (existing != null && !existing.isNull && existing !is ObjectNode) return false
        val target = existing as? ObjectNode
        val differing = fields.filter { f -> (target?.get(f.key)?.takeIf { it.isNumber }?.floatValue() ?: f.default) != f.value }
        if (differing.isEmpty()) return false
        val written = target ?: parent.putObject(name)
        for (f in if (target == null && !omitDefaults) fields else differing) written.set<JsonNode>(f.key, FloatNode.valueOf(f.value))
        return true
    }
}
