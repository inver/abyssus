/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.document.PlacementWrites
import net.nevinsky.abyssus.lib.core.hierarchy.columnRotation
import net.nevinsky.abyssus.lib.core.hierarchy.columnScale

/**
 * Keeps the children bound to a node in place while the node's pivot moves (OpenSpec `model-part-pivots`): every entity
 * whose `ParentComponent.node` is [node] and whose parent draws the MODEL asset [asset] gets
 * `local' = restNew^-1 x restOld x local`, so its world pose stays. Position and rotation change; the scale is kept.
 * [oldRest] and [newRest] are the node's rest transforms within the model before and after the pivot edit.
 */
class CompensateBoundChildren(
    private val asset: String,
    private val node: String,
    oldRest: Matrix4,
    newRest: Matrix4,
) : SceneEdit {
    private val delta: Matrix4 = Matrix4(newRest).inv().mul(oldRest)
    private val placements = PlacementWrites()

    override fun apply(scene: SceneDto): Boolean {
        if (isIdentity(delta)) return false
        var changed = false
        for ((_, entity) in scene.ecs) {
            val link = entity.component(ParentComponent::class.java) ?: continue
            if (link.node != node || link.parentEntityId < 0) continue
            val render = scene.entity(link.parentEntityId.toLong())?.activeComponent(RenderComponent::class.java) ?: continue
            if (render.type != MetaType.MODEL || render.assetName != asset) continue

            val placement = placements.placementOf(entity)
            val local = Matrix4().set(placement.localPosition, placement.localRotation, placement.localScale)
            val moved = Matrix4(delta).mul(local)
            val position = moved.getTranslation(Vector3())
            val rotation = columnRotation(moved, columnScale(moved, Vector3()), Quaternion())
            changed = placements.setPosition(placement, position) or changed
            changed = placements.setRotation(placement, rotation) or changed
        }
        return changed
    }

    private fun isIdentity(m: Matrix4): Boolean {
        val identity = Matrix4().`val`
        return m.`val`.indices.all { kotlin.math.abs(m.`val`[it] - identity[it]) < 1e-7f }
    }
}
