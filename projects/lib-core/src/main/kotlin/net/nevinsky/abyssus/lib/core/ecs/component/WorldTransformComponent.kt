/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3

/**
 * Runtime only (never registered, never read from or written to a file): the entity's world pose, which the
 * `HierarchySystem` resolves through `ParentComponent` links first in each update. [transform] is the world matrix,
 * [parentFrame] the parent's world matrix times the node's rest transform (identity for a root), [child] whether a
 * parent link was followed, and [disabled] whether the entity or one of its ancestors is disabled. [resolved] is false
 * until the system has filled it once.
 */
class WorldTransformComponent : Component {
    val transform = Matrix4()
    val parentFrame = Matrix4()
    var child = false
    var disabled = false
    var resolved = false
}

/**
 * The world matrix of this entity: the resolved [WorldTransformComponent] when there is one, else the transform of its
 * `PositionComponent` (a root in an engine without a hierarchy system), else null.
 */
fun Entity.worldTransform(): Matrix4? =
    getComponent(WorldTransformComponent::class.java)?.takeIf { it.resolved }?.transform
        ?: getComponent(PositionComponent::class.java)?.getTransform()

/** The world position of this entity into [out], or null when it has no pose. */
fun Entity.worldPosition(out: Vector3): Vector3? = worldTransform()?.getTranslation(out)

/** True when this entity is enabled and no ancestor of it is disabled. */
fun Entity.isActiveInHierarchy(): Boolean =
    isActive() && getComponent(WorldTransformComponent::class.java)?.disabled != true
