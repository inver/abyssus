/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.runtime.RuntimeSceneLoader
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.testing.warningsTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsFixtureTest {
    @Test
    fun loadsWithPhysicsComponents() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene(messages)
        // the physics components add no warning to those Untitled's own markers and unmodeled components give
        val untitled = mutableListOf<String>()
        RuntimeSceneLoader(JsonProcessor(), FileLoader(testProject("Untitled")), warningsTo(untitled)).load("Main Scene.scene")
        assertEquals(untitled.map { it.substringAfter(": ") }, messages.map { it.substringAfter(": ") })
        assertTrue(messages.none { "RigidBody" in it || "Collider" in it || "Constraint" in it })

        val model0 = scene.named("Model 0")
        assertEquals(3.086434f, model0.getComponent(PositionComponent::class.java).localPosition.y)
        val body = model0.getComponent(RigidBodyComponent::class.java)
        assertEquals(MotionType.DYNAMIC, body.motionType)
        assertEquals(1f, body.mass)
        val box = model0.getComponent(ColliderComponent::class.java)
        assertEquals(ColliderShape.BOX, box.shape)
        assertEquals(Vector3(0.5f, 0.5f, 0.5f), box.halfExtents)

        val terrain = scene.named("Terrain")
        assertEquals(ColliderShape.HEIGHT_FIELD, terrain.getComponent(ColliderComponent::class.java).shape)
        assertEquals(null, terrain.getComponent(RigidBodyComponent::class.java))

        val rope = scene.named("Model 2").getComponent(ConstraintComponent::class.java)
        assertEquals(ConstraintKind.DISTANCE, rope.kind)
        assertEquals(0, rope.other)
        assertEquals(0f, rope.minDistance)
        assertEquals(3f, rope.maxDistance)
    }
}
