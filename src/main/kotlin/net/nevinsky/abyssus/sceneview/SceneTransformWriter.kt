/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Quat

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.FloatNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.editor.document.SceneEntityTree

/**
 * What a drag changes about an entity: any of its [position], its [rotation] and, for a camera, its view [direction].
 * A null part is left as it is. [target] moves another entity (a light's direction handle) instead of the light itself.
 */
data class TransformEdit(
    val position: Vec3? = null,
    val rotation: Quat? = null,
    val direction: Vec3? = null,
    val target: TargetMove? = null,
)

/** A move of another entity's `PositionComponent.localPosition`: [entityId] is the target, [position] is its new position. */
data class TargetMove(val entityId: String, val position: Vec3)

/** Writes a [TransformEdit] into a scene's JSON tree, touching only the values that change. */
object SceneTransformWriter {
    /**
     * Sets `PositionComponent.localPosition` / `localRotation` of the entity [entityId] under `ecs.entities`, adding
     * missing objects and fields. For an entity with a `CameraComponent.camera` object it also sets that object's
     * `position` and `viewPointPosition`. [target] is written as the target entity's `PositionComponent.localPosition`.
     * Returns false, leaving [root] as it was, when the entity or the target is missing, either has a
     * `PositionComponent` that is not an object, or no value differs.
     */
    fun apply(root: JsonNode, entityId: String, edit: TransformEdit): Boolean {
        val tree = SceneEntityTree(root)
        val components = tree.components(entityId) ?: return false
        // Check every entity the edit touches before writing anything, so a rejected edit leaves [root] as it was.
        if ((edit.position != null || edit.rotation != null) && !holdsPlacement(components)) return false
        val targetComponents = edit.target?.let { tree.components(it.entityId) ?: return false }
        if (targetComponents != null && !holdsPlacement(targetComponents)) return false
        var changed = false
        if (edit.position != null || edit.rotation != null) {
            val placement = placementOf(components)
            edit.position?.let { changed = setVec(placement, "localPosition", it) or changed }
            edit.rotation?.let { changed = setQuat(placement, "localRotation", it) or changed }
        }
        val camera = components.get("CameraComponent")?.get("camera") as? ObjectNode
        if (camera != null) {
            edit.position?.let { changed = setVec(camera, "position", it) or changed }
            edit.direction?.let { changed = setVec(camera, "viewPointPosition", it) or changed }
        }
        if (edit.target != null && targetComponents != null) {
            changed = setVec(placementOf(targetComponents), "localPosition", edit.target.position) or changed
        }
        return changed
    }

    /** Whether [components] has a `PositionComponent` object, or none yet (a missing or null one is added on write). */
    private fun holdsPlacement(components: ObjectNode): Boolean =
        components.get("PositionComponent").let { it == null || it.isNull || it is ObjectNode }

    /** The `PositionComponent` object of [components], added when missing; call only after [holdsPlacement]. */
    private fun placementOf(components: ObjectNode): ObjectNode =
        components.get("PositionComponent") as? ObjectNode ?: components.putObject("PositionComponent")

    private fun setVec(parent: ObjectNode, name: String, v: Vec3) =
        setFields(parent, name, listOf(Field("x", v.x, 0f), Field("y", v.y, 0f), Field("z", v.z, 0f)))

    private fun setQuat(parent: ObjectNode, name: String, q: Quat) =
        setFields(parent, name, listOf(Field("x", q.x, 0f), Field("y", q.y, 0f), Field("z", q.z, 0f), Field("w", q.w, 1f)))

    private class Field(val key: String, val value: Float, val default: Float)

    /**
     * Writes [fields] into the object [name] of [parent] as floats. A missing object is created with every field; an
     * existing one only gets the fields that differ (an absent field holds its default). False when none differs.
     */
    private fun setFields(parent: ObjectNode, name: String, fields: List<Field>): Boolean {
        val existing = parent.get(name)
        if (existing != null && !existing.isNull && existing !is ObjectNode) return false
        val target = existing as? ObjectNode
        val differing = fields.filter { f -> (target?.get(f.key)?.takeIf { it.isNumber }?.floatValue() ?: f.default) != f.value }
        if (differing.isEmpty()) return false
        val written = target ?: parent.putObject(name)
        for (f in if (target == null) fields else differing) written.set<JsonNode>(f.key, FloatNode.valueOf(f.value))
        return true
    }
}
