/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.worldPosition
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.scene.SceneContext
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Child entities in the simulation (`physics-simulation`, "Child entities in the simulation"), on the `Physics`
 * scene. */
class ChildEntitiesTest {
    private val natives = JoltNatives()

    private fun world(scene: SceneContext, messages: MutableList<String>) =
        PhysicsWorld(scene.engine, PhysicsAssets(testProject("Physics")), warningsTo(messages), natives)

    /** Adds entity [id] named [name] under [parent] at [local], with [parts], to the loaded scene (not the file). */
    private fun SceneContext.addChild(
        id: Int,
        name: String,
        parent: Int,
        local: Vector3,
        type: EntityType = EntityType.OBJECT,
        vararg parts: Component,
    ): Entity {
        val entity = Entity()
            .add(HeaderComponent(id = id.toLong(), type = type))
            .add(NameComponent(name))
            .add(PositionComponent(local))
            .add(ParentComponent(parent))
        parts.forEach(entity::add)
        engine.addEntity(entity)
        engine.ids.register(id, entity)
        return entity
    }

    private fun run(world: PhysicsWorld, seconds: Float) = repeat(Math.round(seconds * 60)) { world.advance(1f / 60f) }

    @Test
    fun aRigidBodyOnAChildIsLeftOutWithOneWarning() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene()
        val child = scene.addChild(
            20, "Wing", 0, Vector3(1f, 0f, 0f), EntityType.OBJECT,
            RigidBodyComponent(), ColliderComponent(),
        )
        world(scene, messages).use { world ->
            assertFalse(child in world.entities)
            assertEquals(messages.toString(), 1, messages.size)
            val message = messages.single()
            assertTrue(message, message.contains("Wing (entity 20)") && "rigid body" in message)
            assertTrue(scene.named("Model 0") in world.entities)
            run(world, 1f)
            assertTrue(scene.named("Model 0").getComponent(PositionComponent::class.java).localPosition.y < 3.086434f)
        }
    }

    @Test
    fun aStaticChildColliderSitsAtItsWorldPose() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene()
        val child = scene.addChild(20, "Post", 1, Vector3(1f, 0f, 0f), EntityType.OBJECT, ColliderComponent())
        world(scene, messages).use { world ->
            assertEquals(emptyList<String>(), messages)
            assertEquals(MotionType.STATIC, world.bodyOf(child)!!.motionType)
            val at = world.bodyTransformOf(child)!!.getTranslation(Vector3())
            assertTrue("$at", Vector3(-37.25267f, 0f, -32.7754f).epsilonEquals(at, 1e-4f))
        }
    }

    @Test
    fun aChildColliderUnderAMovingAncestorIsLeftOut() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene()
        val child = scene.addChild(20, "Fin", 0, Vector3(0f, 1f, 0f), EntityType.OBJECT, ColliderComponent())
        world(scene, messages).use { world ->
            assertFalse(child in world.entities)
            assertEquals(messages.toString(), 1, messages.size)
            val message = messages.single()
            assertTrue(message, message.contains("Fin (entity 20)") && "moving entity 0" in message)
            assertTrue(scene.named("Model 0") in world.entities)
        }
    }

    @Test
    fun aFallingBodyCarriesItsDummyWithoutWritingIt() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene()
        val dummy = scene.addChild(20, "Dummy", 0, Vector3(0f, 1f, 0f), EntityType.DUMMY, ColliderComponent())
        world(scene, messages).use { world ->
            assertFalse("a dummy is never a body", dummy in world.entities)
            assertEquals(emptyList<String>(), messages)
            run(world, 5f)
            assertEquals(Vector3(0f, 1f, 0f), dummy.getComponent(PositionComponent::class.java).localPosition)
            assertEquals(
                Vector3(-38.25267f, 0f, -32.7754f),
                scene.named("Terrain").getComponent(PositionComponent::class.java).localPosition,
            )
        }
        // the dummy follows its parent through the hierarchy, in the parent's frame
        scene.engine.update(1f / 60f)
        val model = scene.named("Model 0").getComponent(PositionComponent::class.java).getTransform()
        val expected = Vector3(0f, 1f, 0f).mul(model)
        val drawn = dummy.worldPosition(Vector3())!!
        assertTrue("$drawn vs $expected", expected.epsilonEquals(drawn, 1e-4f))
    }

    @Test
    fun aDisabledParentLeavesItsChildColliderOut() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene()
        scene.named("Terrain").getComponent(HeaderComponent::class.java).entityEnabled = false
        val child = scene.addChild(20, "Post", 1, Vector3(1f, 0f, 0f), EntityType.OBJECT, ColliderComponent())
        world(scene, messages).use { world ->
            assertFalse(child in world.entities)
            assertEquals(emptyList<String>(), messages)
        }
    }
}
