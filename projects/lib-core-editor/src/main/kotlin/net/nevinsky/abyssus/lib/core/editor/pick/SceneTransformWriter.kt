/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Quaternion

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.CameraComponent
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.editor.arrange.ArrangementEdits
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementOwnerOf
import net.nevinsky.abyssus.lib.core.editor.hierarchy.SceneHierarchy
import net.nevinsky.abyssus.lib.core.editor.hierarchy.placementOf
import net.nevinsky.abyssus.lib.core.hierarchy.NodeTransforms
import net.nevinsky.abyssus.lib.core.hierarchy.ResolvedPoses
import net.nevinsky.abyssus.lib.core.hierarchy.localFor
import net.nevinsky.abyssus.lib.core.hierarchy.noNodeTransforms
import net.nevinsky.abyssus.lib.core.hierarchy.shearsChildren
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

/**
 * A move of another entity's `PositionComponent.localPosition`: [entityId] is the target, [position] is its new
 * position.
 */
data class TargetMove(val entityId: Long, val position: Vector3)

/**
 * Writes a [TransformEdit] into a scene, touching only the values that change. The edit's values are world values (what
 * a gizmo drag produces); a child entity is written in its parent's frame (OpenSpec `scene-object-transform`, "Writing
 * a
 * completed drag"), resolved with [nodes] for children bound to a model node. [warn] gets one line when a child is
 * written under a rotated, non-uniformly scaled parent, whose frame shears it (the position stays exact, rotation and
 * scale are the best fit).
 */
class SceneTransformWriter(
    private val arrangements: ArrangementEdits = ArrangementEdits(),
    private val nodes: NodeTransforms = noNodeTransforms(),
    private val warn: (String) -> Unit = {},
) {
    private val placements = PlacementWrites()

    /**
     * Sets the `PositionComponent` local position / rotation of the entity [entityId], adding the component when it is
     * missing. For an entity with a `CameraComponent` it also sets the camera's position and view direction. [edit]'s
     * target is written as the target entity's local position. Moving or turning a copy of a path arrangement detaches
     * it in the same edit ([ArrangementEdits.detach]), so it is then written as a root. Returns false, leaving [scene]
     * as it was, when the entity or the target is missing or no value differs.
     */
    fun apply(scene: SceneDto, entityId: Long, edit: TransformEdit): Boolean {
        val entity = scene.entity(entityId) ?: return false
        val target = edit.target?.let { scene.entity(it.entityId) ?: return false }
        val poses = SceneHierarchy(nodes).resolve(scene)
        var changed = false
        if (edit.position != null || edit.rotation != null) {
            if (arrangementOwnerOf(scene, entityId) != null) {
                val world = placementOf(poses.worldOf(entityId))
                val differs = edit.position?.let { it != world.position } == true ||
                    edit.rotation?.let { it != world.rotation } == true
                if (differs) {
                    arrangements.detach(scene, entityId)
                    val placement = placements.placementOf(entity)
                    edit.position?.let { placements.setPosition(placement, it) }
                    edit.rotation?.let { placements.setRotation(placement, it) }
                    changed = true
                }
            } else {
                val placement = placements.placementOf(entity)
                val local = localEdit(poses, entityId, edit.position, edit.rotation)
                local.position?.let { changed = placements.setPosition(placement, it) or changed }
                local.rotation?.let { changed = placements.setRotation(placement, it) or changed }
            }
        }
        entity.component(CameraComponent::class.java)?.camera?.let { camera ->
            edit.position?.let { if (camera.position != it) {
                camera.position.set(it); changed = true
            } }
            edit.direction?.let { if (camera.direction != it) {
                camera.direction.set(it); changed = true
            } }
        }
        if (edit.target != null && target != null) {
            val local = localEdit(poses, edit.target.entityId, edit.target.position, null)
            changed = placements.setPosition(placements.placementOf(target), local.position!!) or changed
        }
        return changed
    }

    private class LocalValues(val position: Vector3?, val rotation: Quaternion?)

    /**
     * The local values that put [id] at world [position] / [rotation] (either may be null: left as it is). A root, or
     * a child of an identity frame, takes the world values unchanged, so a root's written numbers never move.
     */
    private fun localEdit(poses: ResolvedPoses, id: Long, position: Vector3?, rotation: Quaternion?): LocalValues {
        val frame = poses.parentWorldOf(id)
        if (id !in poses.parents || frame.`val`.contentEquals(IDENTITY)) {
            return LocalValues(position, rotation)
        }
        if (shearsChildren(frame)) {
            warn(
                "entity $id: its parent's rotation and non-uniform scale shear it; its rotation and scale are " +
                    "approximated"
            )
        }
        val localPosition = position?.let { Vector3(it).mul(Matrix4(frame).inv()) }
        val localRotation = rotation?.let {
            val current = placementOf(poses.worldOf(id))
            localFor(frame, Matrix4().set(position ?: current.position, it, current.scale)).pose.rotation
        }
        return LocalValues(localPosition, localRotation)
    }
}

private val IDENTITY = Matrix4().`val`.copyOf()
