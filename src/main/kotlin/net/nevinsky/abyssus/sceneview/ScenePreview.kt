/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.sceneview.gizmo.DragResult

/** The entity a gizmo acts on: where it is, and the direction it faces when it has one (a camera, a directional or spot light). */
class Selected(val transform: PlacementTransform, val direction: Vec3?)

/**
 * Where a rotate drag on a handle-aimed light must put its handle: the light's position plus the turned direction times
 * the start distance from the light to its handle (1 when that distance is 0). Null when [entityId] is not a
 * handle-aimed light or has no turned direction, so callers can leave the handle where it is.
 */
object ScenePreview {
    /** The new position of a handle-aimed light's direction handle for a rotate [result], or null when none applies. */
    fun aimedTarget(content: SceneContent, entityId: String, result: DragResult): Vec3? {
        val light = content.lights.firstOrNull { it.entityId == entityId } ?: return null
        val handleId = light.lookAtId ?: return null
        if (handleId !in content.handleIds) return null
        val handle = content.entityPositions[handleId] ?: return null
        val direction = result.direction ?: return null
        val dx = handle.x - light.position.x
        val dy = handle.y - light.position.y
        val dz = handle.z - light.position.z
        val distance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).takeIf { it > 1e-6f } ?: 1f
        val p = result.transform.position
        return Vec3(p.x + direction.x * distance, p.y + direction.y * distance, p.z + direction.z * distance)
    }

    /** [content] with the entity [entityId] shown at [result]; [content] itself when there is no such entity. */
    fun apply(content: SceneContent, entityId: String, result: DragResult): SceneContent {
        val t = result.transform
        val light = content.lights.firstOrNull { it.entityId == entityId }
        val handleAimed = light != null && light.lookAtId != null && light.lookAtId in content.handleIds
        // A rotate drag keeps the light's position and turns its direction; it moves the handle. A move drag changes the
        // position and re-aims a look-at light at its unmoved target. The two are told apart by whether the position moved.
        val rotatingHandleAimed = handleAimed && t.position == light!!.position && result.direction != null
        val moved = content.entityPositions + (entityId to t.position)
        val positions = if (rotatingHandleAimed) moved + (light!!.lookAtId to aimedTarget(content, entityId, result)!!) else moved
        return content.copy(
            models = content.models.map { if (it.entityId == entityId) it.copy(transform = t) else it },
            terrains = content.terrains.map { if (it.entityId == entityId) it.copy(transform = t) else it },
            lights = content.lights.map {
                if (it.entityId != entityId) it
                else {
                    val target = it.lookAtId?.let(content.entityPositions::get)
                    val direction = when {
                        // A move re-aims a look-at light at its unmoved target; a rotate uses the turned direction.
                        target != null && t.position != it.position -> SceneContent.aim(t.position, target) ?: SceneContent.forward(t.rotation)
                        else -> result.direction ?: SceneContent.forward(t.rotation)
                    }
                    it.copy(position = t.position, rotation = t.rotation, direction = direction)
                }
            },
            cameras = content.cameras.map {
                if (it.entityId != entityId) it
                else it.copy(position = t.position, rotation = t.rotation, direction = result.direction ?: it.direction)
            },
            entityPositions = if (entityId in content.entityPositions) positions else content.entityPositions,
        )
    }

    fun apply(content: SceneContent, preview: Map<String, DragResult>): SceneContent =
        preview.entries.fold(content) { c, (id, result) -> apply(c, id, result) }

    /** The transform of the model, terrain, camera or light [entityId], or null when the scene has no such entity. */
    fun selected(content: SceneContent, entityId: String): Selected? {
        content.models.firstOrNull { it.entityId == entityId }?.let { return Selected(it.transform, null) }
        content.terrains.firstOrNull { it.entityId == entityId }?.let { return Selected(it.transform, null) }
        content.cameras.firstOrNull { it.entityId == entityId }?.let {
            val direction = CameraFrustum.directionOf(it, content.entityPositions)
            return Selected(PlacementTransform(it.position, it.rotation, Vec3(1f, 1f, 1f)), direction)
        }
        content.lights.firstOrNull { it.entityId == entityId }?.let {
            val facing = if (it.kind == LightKind.POINT) null else it.direction
            return Selected(PlacementTransform(it.position, it.rotation, Vec3(1f, 1f, 1f)), facing)
        }
        return null
    }

    fun isCamera(content: SceneContent, entityId: String): Boolean = content.cameras.any { it.entityId == entityId }

    /** Whether the scene still has [entityId] as something the view can select. */
    fun contains(content: SceneContent, entityId: String): Boolean = selected(content, entityId) != null
}