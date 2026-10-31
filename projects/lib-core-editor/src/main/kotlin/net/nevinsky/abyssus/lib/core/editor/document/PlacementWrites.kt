/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent

/**
 * The one rule for writing a position, rotation or scale into an entity's [PositionComponent], used by the gizmo,
 * arrangements and paste, so they share one set of rules. Each write reports whether the value differed.
 */
class PlacementWrites {
    /** Sets the local position of [placement] to [v]; false when it already is [v]. */
    fun setPosition(placement: PositionComponent, v: Vector3): Boolean {
        if (placement.localPosition == v) return false
        placement.localPosition.set(v)
        return true
    }

    /** Sets the local rotation of [placement] to [q]; false when it already is [q]. */
    fun setRotation(placement: PositionComponent, q: Quaternion): Boolean {
        if (placement.localRotation == q) return false
        placement.localRotation.set(q)
        return true
    }

    /** The [PositionComponent] of [entity], added when it has none. */
    fun placementOf(entity: EntityDto): PositionComponent =
        entity.component(PositionComponent::class.java)
            ?: PositionComponent().also(entity::add)
}
