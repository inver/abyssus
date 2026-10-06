/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.runtime.schema.ComponentRegistry
import net.nevinsky.abyssus.lib.runtime.schema.EntityRef
import net.nevinsky.abyssus.lib.runtime.schema.Field
import net.nevinsky.abyssus.lib.runtime.schema.SceneComponent

/** How a body moves: not at all, only where the game moves it, or under forces and collisions. */
enum class MotionType { STATIC, KINEMATIC, DYNAMIC }

/** The shape of a [ColliderComponent]; a hull comes from the entity's model, a height field from its terrain. */
enum class ColliderShape { BOX, SPHERE, CAPSULE, CONVEX_HULL, HEIGHT_FIELD }

/** A [ConstraintComponent]'s kind: a distance range (a rope when the minimum is `0`), a hinge or a fixed joint. */
enum class ConstraintKind { DISTANCE, HINGE, FIXED }

/** Makes its entity a body. Without a [ColliderComponent] the entity is left out of the simulation. */
@SceneComponent("RigidBodyComponent", label = "Rigid body")
class RigidBodyComponent : Component {
    @Field(label = "Motion type")
    var motionType = MotionType.DYNAMIC

    @Field(label = "Mass (kg)", min = 0.0, minExclusive = true)
    var mass = 1f

    @Field(label = "Friction", min = 0.0)
    var friction = 0.2f

    @Field(label = "Restitution", min = 0.0, max = 1.0)
    var restitution = 0f

    @Field(label = "Linear damping", group = "Damping", min = 0.0)
    var linearDamping = 0.05f

    @Field(label = "Angular damping", group = "Damping", min = 0.0)
    var angularDamping = 0.05f

    @Field(label = "Gravity factor")
    var gravityFactor = 1f
}

/** The shape of its entity's body, at [offset] from the entity and scaled by the entity's scale. */
@SceneComponent("ColliderComponent", label = "Collider")
class ColliderComponent : Component {
    @Field(label = "Shape")
    var shape = ColliderShape.BOX

    @Field(label = "Half extents", group = "Box", min = 0.0, minExclusive = true)
    var halfExtents = Vector3(0.5f, 0.5f, 0.5f)

    @Field(label = "Radius", group = "Sphere and capsule", min = 0.0, minExclusive = true)
    var radius = 0.5f

    @Field(label = "Half height", group = "Sphere and capsule", min = 0.0, minExclusive = true)
    var halfHeight = 0.5f

    @Field(label = "Offset")
    var offset = Vector3()
}

/** Joins its entity's body to [other]'s, or to a fixed point in the world when [other] is `-1`. */
@SceneComponent("ConstraintComponent", label = "Constraint")
class ConstraintComponent : Component {
    @Field(label = "Kind")
    var kind = ConstraintKind.DISTANCE

    @Field(label = "Other entity")
    @EntityRef
    var other = -1

    /** On this entity's body, in its local space. */
    @Field(label = "Anchor")
    var anchor = Vector3()

    /** On [other]'s body in its local space; a world point when there is no other body. */
    @Field(label = "Other anchor")
    var otherAnchor = Vector3()

    @Field(label = "Minimum distance", group = "Distance", min = 0.0)
    var minDistance = 0f

    @Field(label = "Maximum distance", group = "Distance", min = 0.0)
    var maxDistance = 1f

    @Field(label = "Hinge axis", group = "Hinge")
    var hingeAxis = Vector3(0f, 1f, 0f)
}

/** The physics components, for the scene loader, the schema export and Abyssus Physics's bundled schema. */
class PhysicsComponents : ComponentRegistry {
    override fun components() = listOf(RigidBodyComponent::class.java, ColliderComponent::class.java, ConstraintComponent::class.java)
}
