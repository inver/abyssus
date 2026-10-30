/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.scene.SceneContext
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.CAPSULE
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.box
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.layer
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.layerJson
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.scene
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Foliage colliders in a native world: a box dropped on a capsule trunk settles on it, and every failure is isolated. */
class PhysicsFoliageTest {
    private val project = FoliageProject()
    private val natives = JoltNatives()
    private val messages = mutableListOf<String>()
    private val trunk = listOf(FoliageCopy(0, 50f, 50f, 0f, 1f))

    @After
    fun cleanUp() = project.close()

    private fun load(text: String): SceneContext =
        requireNotNull(physicsLoader(project.dir, warningsTo(messages)).loadFromText(text)) { messages.joinToString("\n") }

    private fun world(scene: SceneContext) = PhysicsWorld(scene.engine, PhysicsAssets(project.dir), warningsTo(messages), natives)

    private fun ready(collider: String? = CAPSULE, kind: String = "OBJECT", stale: Boolean = false) {
        project.foliage("[${layerJson(collider, kind = kind)}]")
        project.bake(listOf(layer(trunk)), stale = stale)
    }

    /** The box's height after [seconds] of falling, and every contact pair seen on the way. */
    private fun drop(world: PhysicsWorld, scene: SceneContext, seconds: Float = 3f): Pair<Float, Set<Set<String>>> {
        val seen = mutableSetOf<Set<String>>()
        repeat(Math.round(seconds * 60)) {
            world.advance(1f / 60f)
            world.contacts().forEach { seen.add(setOf(scene.nameOf(it.a), scene.nameOf(it.b))) }
        }
        return scene.named("Box").getComponent(PositionComponent::class.java).localPosition.y to seen
    }

    private fun SceneContext.nameOf(e: com.badlogic.ashley.core.Entity) =
        e.getComponent(net.nevinsky.abyssus.lib.core.ecs.component.NameComponent::class.java).name.orEmpty()

    @Test
    fun aBoxSettlesOnACapsuleAndTheContactNamesTheTerrainOwner() {
        ready()
        val scene = load(scene(extra = box(50f, 5f, 50f)))
        world(scene).use { world ->
            assertEquals(emptyList<String>(), messages)
            assertEquals(1, world.foliageBodies)
            assertFalse(scene.named("Ground") in world.entities) // the owner has no ordinary body
            val (y, contacts) = drop(world, scene)
            assertEquals(2.0f, y, 0.05f) // capsule top at 1.5 m, plus half the box
            assertTrue(contacts.toString(), setOf("Box", "Ground") in contacts)
        }
    }

    @Test
    fun noColliderLetsTheBoxReachTheFloor() {
        ready(collider = null)
        val scene = load(scene(extra = box(50f, 5f, 50f)))
        world(scene).use { world ->
            assertEquals(0, world.foliageBodies)
            assertEquals(-18.5f, drop(world, scene).first, 0.05f)
            assertEquals(emptyList<String>(), messages)
        }
    }

    @Test
    fun anImportedDetailColliderIsIgnoredWithAWarning() {
        ready(kind = "DETAIL")
        val scene = load(scene(extra = box(50f, 5f, 50f)))
        world(scene).use { world ->
            assertEquals(0, world.foliageBodies)
            assertEquals(-18.5f, drop(world, scene).first, 0.05f)
        }
        assertTrue(messages.toString(), messages.single().contains("detail layer"))
    }

    @Test
    fun anInvalidColliderDoesNotCollideAndDoesNotStopTheScene() {
        ready(collider = """{"shape":"CAPSULE","radius":0,"halfHeight":1}""")
        val scene = load(scene(extra = box(50f, 5f, 50f)))
        world(scene).use { world ->
            assertEquals(0, world.foliageBodies)
            assertEquals(-18.5f, drop(world, scene).first, 0.05f)
        }
        assertEquals(1, messages.size)
        assertTrue(messages.single(), messages.single().contains("unusable collider"))
    }

    @Test
    fun aMovingOwnerGetsNoFoliageCollision() {
        ready()
        val moving = ""","RigidBodyComponent":{"motionType":"KINEMATIC"},"ColliderComponent":{"shape":"BOX","halfExtents":{"x":0.1,"y":0.1,"z":0.1}}"""
        val scene = load(scene(terrainExtra = moving, extra = box(50f, 5f, 50f)))
        world(scene).use { world ->
            assertEquals(0, world.foliageBodies)
            assertEquals(-18.5f, drop(world, scene).first, 0.05f)
        }
        assertEquals(1, messages.count { it.contains("KINEMATIC") })
    }

    @Test
    fun aStaleBakeStillCollidesWithOneWarning() {
        ready(stale = true)
        val scene = load(scene(extra = box(50f, 5f, 50f)))
        world(scene).use { world ->
            assertEquals(1, world.foliageBodies)
            assertEquals(2.0f, drop(world, scene).first, 0.05f)
        }
        assertEquals(1, messages.size)
        assertTrue(messages.single(), messages.single().contains("changed after its last bake"))
    }

    @Test
    fun aMissingBakeHasNoFoliageCollisionAndOneWarning() {
        ready()
        project.deleteBake()
        val scene = load(scene(extra = box(50f, 5f, 50f)))
        world(scene).use { world ->
            assertEquals(0, world.foliageBodies)
            assertEquals(-18.5f, drop(world, scene).first, 0.05f)
        }
        assertEquals(1, messages.size)
        assertNotNull(messages.single().takeIf { it.contains("no bake") })
    }
}
