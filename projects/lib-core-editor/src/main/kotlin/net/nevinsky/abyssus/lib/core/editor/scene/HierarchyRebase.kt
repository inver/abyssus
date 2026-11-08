/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.document.PlacementWrites
import net.nevinsky.abyssus.lib.core.editor.hierarchy.SceneHierarchy
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.hierarchy.localFor
import net.nevinsky.abyssus.lib.core.ecs.component.localPoseOf

/**
 * The one-off rewrite of a scene written when `ParentComponent` was only a link (OpenSpec change
 * `add-entity-hierarchy-and-dummies`): every child's `PositionComponent`, which then held its world pose, is rewritten
 * as the pose local to its parent, so nothing moves. The parent's stored pose is what that world pose is taken
 * relative to (under the old meaning every stored pose was a world pose). A `HANDLE` gets only its position rewritten:
 * it carries no orientation. Roots, and children of an identity pose (the copies of an unplaced arrangement), are left
 * as they are. Not a load-time migration: it is run once over committed files.
 */
class HierarchyRebase : SceneEdit {
    private val placements = PlacementWrites()

    override fun apply(scene: SceneDto): Boolean {
        val parents = SceneHierarchy().resolve(scene).parents
        // old world poses first, before any child is rewritten
        val oldWorld = scene.ecs.mapNotNull { (id, entity) ->
            entity.component(PositionComponent::class.java)?.let { id to localPoseOf(it).matrix() }
        }.toMap()
        var changed = false
        for ((id, parentId) in parents.entries.sortedBy { it.key }) {
            val entity = scene.entity(id) ?: continue
            if (entity.component(ParentComponent::class.java) == null) {
                continue
            }
            val parentWorld = oldWorld[parentId] ?: Matrix4()
            if (parentWorld.`val`.contentEquals(Matrix4().`val`)) {
                continue
            }
            val world = oldWorld[id] ?: Matrix4()
            val local = localFor(parentWorld, world).pose
            val placement = placements.placementOf(entity)
            changed = placements.setPosition(placement, local.position) or changed
            if (entity.header().type != EntityType.HANDLE) {
                if (!local.rotation.isIdentity(1e-6f) || placement.localRotation != Quaternion()) {
                    changed = placements.setRotation(placement, local.rotation) or changed
                }
                if (!local.scale.epsilonEquals(placement.localScale, 1e-6f)) {
                    placement.localScale = Vector3(local.scale)
                    changed = true
                }
            }
        }
        return changed
    }
}
