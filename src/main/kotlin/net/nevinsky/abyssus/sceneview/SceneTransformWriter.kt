/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.FloatNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * What a drag changes about an entity: any of its [position], its [rotation] and, for a camera, its view [direction].
 * A null part is left as it is.
 */
data class TransformEdit(val position: Vec3? = null, val rotation: Quat? = null, val direction: Vec3? = null)

/** Writes a [TransformEdit] into a scene's JSON tree, touching only the values that change. */
object SceneTransformWriter {
    /**
     * Sets `PositionComponent.localPosition` / `localRotation` of the entity [entityId] under `ecs.entities`, adding
     * missing objects and fields. For an entity with a `CameraComponent.camera` object it also sets that object's
     * `position` and `viewPointPosition`. Returns false, leaving [root] as it was, when the entity is missing or no
     * value differs.
     */
    fun apply(root: JsonNode, entityId: String, edit: TransformEdit): Boolean {
        val components = root.get("ecs")?.get("entities")?.get(entityId)?.get("components") as? ObjectNode ?: return false
        var changed = false
        if (edit.position != null || edit.rotation != null) {
            val existing = components.get("PositionComponent")
            val placement = when {
                existing == null || existing.isNull -> components.putObject("PositionComponent")
                existing is ObjectNode -> existing
                else -> return false
            }
            edit.position?.let { changed = setVec(placement, "localPosition", it) or changed }
            edit.rotation?.let { changed = setQuat(placement, "localRotation", it) or changed }
        }
        val camera = components.get("CameraComponent")?.get("camera") as? ObjectNode
        if (camera != null) {
            edit.position?.let { changed = setVec(camera, "position", it) or changed }
            edit.direction?.let { changed = setVec(camera, "viewPointPosition", it) or changed }
        }
        return changed
    }

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
