/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.github.stephengold.joltjni.BodyInterface
import com.github.stephengold.joltjni.Quat
import com.github.stephengold.joltjni.RVec3
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.physics.MotionType

/** Reads native poses for entity write-back and rope anchors; the entity's scale is always retained. */
internal class PhysicsPoseWriter(
    private val bodies: BodyInterface,
    private val records: Collection<PhysicsWorld.BodyRecord>,
) {
    fun writeBack() {
        val p = RVec3()
        val q = Quat()
        for (record in records) {
            if (record.motion == MotionType.STATIC) continue
            bodies.getPositionAndRotation(record.id, p, q)
            val position = record.entity.getComponent(PositionComponent::class.java) ?: continue
            position.localPosition.set(p.x(), p.y(), p.z())
            position.localRotation.set(q.getX(), q.getY(), q.getZ(), q.getW())
        }
    }

    fun transformOf(record: PhysicsWorld.BodyRecord): Matrix4 {
        val p = RVec3()
        val q = Quat()
        bodies.getPositionAndRotation(record.id, p, q)
        val scale = record.entity.getComponent(PositionComponent::class.java)?.localScale ?: Vector3(1f, 1f, 1f)
        return Matrix4().set(Vector3(p.x(), p.y(), p.z()), Quaternion(q.getX(), q.getY(), q.getZ(), q.getW()), scale)
    }
}
