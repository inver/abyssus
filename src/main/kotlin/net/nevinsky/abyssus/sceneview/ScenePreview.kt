/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.PlacementTransform
import net.nevinsky.abyssus.editor.content.LightKind

import net.nevinsky.abyssus.sceneview.gizmo.DragResult

/** The entity a gizmo acts on: where it is, and the direction it faces when it has one (a camera, a directional or spot light). */
class Selected(val transform: PlacementTransform, val direction: Vec3?)

/** Applies the transform an in-progress drag proposes over the scene's placements, and finds the selection's transform. No GL needed. */
object ScenePreview {
    /**
     * Where a rotate drag on a handle-aimed light must put its handle: the light's position plus the turned direction
     * times the start distance from the light to its handle (1 when that distance is 0). Null when [entityId] is not a
     * handle-aimed light or has no turned direction, so callers can leave the handle where it is.
     */
    fun aimedTarget(content: SceneContent, entityId: String, result: DragResult): Vec3? {
        val light = content.lights.firstOrNull { it.entityId == entityId } ?: return null
        val handleId = content.aimHandleOf(light) ?: return null
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
        // A rotate drag keeps the light's position and turns its direction; it moves the handle. A move drag changes the
        // position and re-aims a look-at light at its unmoved target. The two are told apart by whether the position moved.
        val handleId = light?.let(content::aimHandleOf)?.takeIf { t.position == light.position }
        val handleAt = handleId?.let { aimedTarget(content, entityId, result) }
        val moved = content.entityPositions + (entityId to t.position)
        val positions = if (handleId != null && handleAt != null) moved + (handleId to handleAt) else moved
        return content.copy(
            models = content.models.map { if (it.entityId == entityId) it.copy(transform = t) else it },
            terrains = content.terrains.map { if (it.entityId == entityId) it.copy(transform = t) else it },
            lights = content.lights.map {
                if (it.entityId != entityId) it
                else {
                    // A point light has no direction, so it never re-aims.
                    val target = it.lookAtId?.takeIf { _ -> it.kind != LightKind.POINT }?.let(content.entityPositions::get)
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

    /**
     * [content] with each entity of [poses] placed at its pose instead of its authored placement, keeping its authored
     * scale; a light faces along its posed rotation unless it looks at a target. Entities the scene lacks are ignored.
     */
    fun withPoses(content: SceneContent, poses: Map<String, Pose>): SceneContent = poses.entries.fold(content) { c, (id, pose) ->
        val scale = c.models.firstOrNull { it.entityId == id }?.transform?.scale
            ?: c.terrains.firstOrNull { it.entityId == id }?.transform?.scale ?: Vec3(1f, 1f, 1f)
        apply(c, id, DragResult(PlacementTransform(pose.position, pose.rotation, scale), null))
    }

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
