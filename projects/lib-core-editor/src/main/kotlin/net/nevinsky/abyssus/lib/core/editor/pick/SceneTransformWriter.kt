/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Quaternion

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.CameraComponent
import net.nevinsky.abyssus.lib.core.editor.arrange.ArrangementEdits
import net.nevinsky.abyssus.lib.core.editor.document.PlacementWrites

/**
 * What a drag changes about an entity: any of its [position], its [rotation] and, for a camera, its view [direction].
 * A null part is left as it is. [target] moves another entity (a light's direction handle) instead of the light itself.
 */
data class TransformEdit(
    val position: Vector3? = null,
    val rotation: Quaternion? = null,
    val direction: Vector3? = null,
    val target: TargetMove? = null,
)

/** A move of another entity's `PositionComponent.localPosition`: [entityId] is the target, [position] is its new position. */
data class TargetMove(val entityId: Long, val position: Vector3)

/** Writes a [TransformEdit] into a scene, touching only the values that change. */
class SceneTransformWriter(private val arrangements: ArrangementEdits = ArrangementEdits()) {
    private val placements = PlacementWrites()

    /**
     * Sets the `PositionComponent` local position / rotation of the entity [entityId], adding the component when it is
     * missing. For an entity with a `CameraComponent` it also sets the camera's position and view direction. [edit]'s
     * target is written as the target entity's local position. Moving or turning a copy of a path arrangement detaches it
     * in the same edit ([ArrangementEdits.detach]). Returns false, leaving [scene] as it was, when the entity or the
     * target is missing or no value differs.
     */
    fun apply(scene: SceneDto, entityId: Long, edit: TransformEdit): Boolean {
        val entity = scene.entity(entityId) ?: return false
        val target = edit.target?.let { scene.entity(it.entityId) ?: return false }
        var changed = false
        if (edit.position != null || edit.rotation != null) {
            val placement = placements.placementOf(entity)
            edit.position?.let { changed = placements.setPosition(placement, it) or changed }
            edit.rotation?.let { changed = placements.setRotation(placement, it) or changed }
            if (changed) arrangements.detach(scene, entityId)
        }
        entity.components.filterIsInstance<CameraComponent>().firstOrNull()?.camera?.let { camera ->
            edit.position?.let { if (camera.position != it) { camera.position.set(it); changed = true } }
            edit.direction?.let { if (camera.direction != it) { camera.direction.set(it); changed = true } }
        }
        if (edit.target != null && target != null) {
            changed = placements.setPosition(placements.placementOf(target), edit.target.position) or changed
        }
        return changed
    }
}
