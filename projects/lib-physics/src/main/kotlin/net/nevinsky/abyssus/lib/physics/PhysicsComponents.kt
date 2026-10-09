/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3

/** How a body moves: not at all, only where the game moves it, or under forces and collisions. */
enum class MotionType { STATIC, KINEMATIC, DYNAMIC }

/** The shape of a [ColliderComponent]; a hull comes from the entity's model, a height field from its terrain. */
enum class ColliderShape { BOX, SPHERE, CAPSULE, CONVEX_HULL, HEIGHT_FIELD }

/** A [ConstraintComponent]'s kind: a distance range (a rope when the minimum is `0`), a hinge or a fixed joint. */
enum class ConstraintKind { DISTANCE, HINGE, FIXED }

/** Makes its entity a body. Without a [ColliderComponent] the entity is left out of the simulation. */
class RigidBodyComponent : Component {
    var motionType = MotionType.DYNAMIC
    var mass = 1f
    var friction = 0.2f
    var restitution = 0f
    var linearDamping = 0.05f
    var angularDamping = 0.05f
    var gravityFactor = 1f
}

/** The shape of its entity's body, at [offset] from the entity and scaled by the entity's scale. */
class ColliderComponent : Component {
    var shape = ColliderShape.BOX
    var halfExtents = Vector3(0.5f, 0.5f, 0.5f)
    var radius = 0.5f
    var halfHeight = 0.5f
    var offset = Vector3()
}

/** Joins its entity's body to [other]'s, or to a fixed point in the world when [other] is `-1`. */
class ConstraintComponent : Component {
    var kind = ConstraintKind.DISTANCE
    var other = -1

    /** On this entity's body, in its local space. */
    var anchor = Vector3()

    /** On [other]'s body in its local space; a world point when there is no other body. */
    var otherAnchor = Vector3()
    var minDistance = 0f
    var maxDistance = 1f
    var hingeAxis = Vector3(0f, 1f, 0f)
}

/** The physics components by the name a scene file gives them, for [net.nevinsky.abyssus.lib.gdx.ecs.ComponentRegistry.registerAll]. */
val PHYSICS_COMPONENTS: Map<String, Class<out Component>> = mapOf(
    "RigidBodyComponent" to RigidBodyComponent::class.java,
    "ColliderComponent" to ColliderComponent::class.java,
    "ConstraintComponent" to ConstraintComponent::class.java,
)
