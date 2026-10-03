/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.sceneview.gizmo.DragResult

/** The entity a gizmo acts on: where it is, and the direction it faces when it has one (a camera, a directional or spot light). */
class Selected(val transform: PlacementTransform, val direction: Vec3?)

/** Applies the transform an in-progress drag proposes over the scene's placements, and finds the selection's transform. No GL needed. */
object ScenePreview {
    /** [content] with the entity [entityId] shown at [result]; [content] itself when there is no such entity. */
    fun apply(content: SceneContent, entityId: String, result: DragResult): SceneContent {
        val t = result.transform
        val moved = content.entityPositions + (entityId to t.position)
        return content.copy(
            models = content.models.map { if (it.entityId == entityId) it.copy(transform = t) else it },
            terrains = content.terrains.map { if (it.entityId == entityId) it.copy(transform = t) else it },
            lights = content.lights.map {
                if (it.entityId != entityId) it
                else it.copy(position = t.position, rotation = t.rotation, direction = result.direction ?: SceneContent.forward(t.rotation))
            },
            cameras = content.cameras.map {
                if (it.entityId != entityId) it
                else it.copy(position = t.position, rotation = t.rotation, direction = result.direction ?: it.direction)
            },
            entityPositions = if (entityId in content.entityPositions) moved else content.entityPositions,
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
