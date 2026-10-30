/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.github.stephengold.joltjni.*
import com.github.stephengold.joltjni.enumerate.EActivation
import com.github.stephengold.joltjni.enumerate.EMotionType
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.physics.*

/** Validates and creates ordinary bodies; the world retains ownership of native resources and body records. */
internal class PhysicsBodyFactory(
    private val bodies: BodyInterface,
    private val shapes: ShapeFactory,
    private val staticLayer: Int,
    private val movingLayer: Int,
    private val retain: (JoltPhysicsObject) -> JoltPhysicsObject,
    private val entityId: (Entity) -> Int,
    private val warn: (Entity, String) -> Unit,
) {
    sealed interface Built {
        class Refused(val reason: String) : Built
        class Created(val id: Int, val body: Body, val motion: MotionType, val mass: Float, val gravityFactor: Float) : Built
    }

    /** Creates [entity]'s body; returns why it cannot instead. */
    fun create(entity: Entity, body: RigidBodyComponent?, collider: ColliderComponent): Built {
        val motion = body?.motionType ?: MotionType.STATIC
        // a moving body's pose is written back, so it needs a position component
        val position = entity.getComponent(PositionComponent::class.java)
            ?: PositionComponent().also { if (motion != MotionType.STATIC) entity.add(it) }
        listOfNotNull(
            nonFinite("PositionComponent.localPosition", position.localPosition),
            nonFinite("PositionComponent.localScale", position.localScale),
            nonFinite("PositionComponent.localRotation", position.localRotation.x, position.localRotation.y, position.localRotation.z, position.localRotation.w),
        ).firstOrNull()?.let { return Built.Refused(it) }
        if (body != null) {
            listOf("mass" to body.mass, "friction" to body.friction, "restitution" to body.restitution,
                "linearDamping" to body.linearDamping, "angularDamping" to body.angularDamping, "gravityFactor" to body.gravityFactor,
            ).firstOrNull { !it.second.isFinite() }?.let { return Built.Refused("RigidBodyComponent.${it.first} is not finite (${it.second})") }
            if (motion == MotionType.DYNAMIC && body.mass <= 0f) return Built.Refused("RigidBodyComponent.mass ${body.mass} is not greater than 0")
            if (body.friction < 0f || body.linearDamping < 0f || body.angularDamping < 0f || body.restitution < 0f) {
                return Built.Refused("RigidBodyComponent has a negative friction, restitution or damping")
            }
        }
        if (collider.shape == ColliderShape.HEIGHT_FIELD && motion != MotionType.STATIC) return Built.Refused("a height field can only be static, not $motion")
        val rotation = Quaternion(position.localRotation).nor()
        val shape = when (val built = shapes.build(entity, collider, position.localScale)) {
            is ShapeFactory.Built.Refused -> return Built.Refused(built.reason)
            is ShapeFactory.Built.Shape -> {
                built.warning?.let { warn(entity, it) }
                built.ref
            }
        }
        if (entity.getComponent(ParentComponent::class.java)?.parentEntityId?.let { it != NO_ENTITY } == true) {
            warn(entity, "has a parent, which physics ignores: its body moves in world space")
        }
        val p = position.localPosition
        val settings = own(BodyCreationSettings(
            shape, RVec3(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()), Quat(rotation.x, rotation.y, rotation.z, rotation.w),
            jolt(motion), if (motion == MotionType.STATIC) staticLayer else movingLayer,
        ))
        settings.setFriction(body?.friction ?: 0.2f)
        settings.setRestitution(body?.restitution ?: 0f)
        if (body != null) {
            settings.setLinearDamping(body.linearDamping)
            settings.setAngularDamping(body.angularDamping)
            settings.setGravityFactor(body.gravityFactor)
            if (motion == MotionType.DYNAMIC) {
                settings.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia)
                settings.setMassPropertiesOverride(own(MassProperties()).setMass(body.mass))
            }
        }
        settings.setUserData(entityId(entity).toLong())
        val created = bodies.createBody(settings)
        val id = created.getId()
        bodies.addBody(id, if (motion == MotionType.STATIC) EActivation.DontActivate else EActivation.Activate)
        return Built.Created(id, created, motion, if (motion == MotionType.DYNAMIC) body!!.mass else 0f, body?.gravityFactor ?: 1f)
    }

    private fun jolt(motion: MotionType) = when (motion) {
        MotionType.STATIC -> EMotionType.Static
        MotionType.KINEMATIC -> EMotionType.Kinematic
        MotionType.DYNAMIC -> EMotionType.Dynamic
    }

    private fun nonFinite(name: String, v: Vector3): String? = nonFinite(name, v.x, v.y, v.z)
    private fun nonFinite(name: String, vararg values: Float): String? =
        if (values.all { it.isFinite() }) null else "$name is not finite (${values.joinToString(", ")})"

    private fun <T : JoltPhysicsObject> own(value: T): T = value.also { retain(it) }
}
