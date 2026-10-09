/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import net.nevinsky.abyssus.lib.core.scene.SceneContext
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import net.nevinsky.abyssus.lib.gdx.testing.failOnWarnings
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Base64

class PhysicsWorldTest {
    private val natives = JoltNatives()

    private fun world(scene: SceneContext, messages: MutableList<String>, project: File = testProject("Physics")) =
        PhysicsWorld(scene.engine, PhysicsAssets(project), warningsTo(messages), natives)

    private fun SceneContext.position(name: String) = named(name).getComponent(PositionComponent::class.java)

    /** Advances [world] by [seconds] in calls of 1/60 s. */
    private fun run(world: PhysicsWorld, seconds: Float) = repeat(Math.round(seconds * 60)) { world.advance(1f / 60f) }

    // ---- build and close ----

    @Test
    fun theFixtureBuildsWithoutWarnings() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene()
        world(scene, messages).use { world ->
            assertEquals(emptyList<String>(), messages)
            assertEquals(setOf("Model 0", "Terrain", "Model 2").map(scene::named).toSet(), world.entities)
            assertEquals(1, world.constraints.size)
            assertEquals(MotionType.STATIC, world.bodyOf(scene.named("Terrain"))!!.motionType)
            assertEquals(1f, world.bodyOf(scene.named("Model 0"))!!.mass)
        }
    }

    @Test
    fun rigidBodyWithoutColliderIsLeftOutWithOneWarning() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene()
        scene.named("Model 0").remove(ColliderComponent::class.java)
        scene.named("Model 2").remove(ConstraintComponent::class.java)
        world(scene, messages).use { world ->
            assertEquals(1, messages.size)
            assertTrue(messages.single(), messages.single().contains("Model 0 (entity 0)") && messages.single().contains("no collider"))
            assertFalse(scene.named("Model 0") in world.entities)
            assertTrue(scene.named("Terrain") in world.entities)
        }
    }

    @Test
    fun flatModelHullIsRefused() {
        val project = Files.createTempDirectory("physics-flat").toFile()
        try {
            writeFlatModel(File(project, "assets/flat"))
            val messages = mutableListOf<String>()
            val scene = requireNotNull(physicsLoader(project, warningsTo(messages)).loadFromText(
                """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{
                  "0":{"components":{"NameComponent":{"name":"Flat"},"ColliderComponent":{"shape":"CONVEX_HULL"},
                    "RenderComponent":{"shaderKey":"defaultShader","type":"MODEL","assetName":"flat"}}},
                  "1":{"components":{"NameComponent":{"name":"Ball"},"PositionComponent":{"localPosition":{"y":5}},
                    "RigidBodyComponent":{},"ColliderComponent":{"shape":"SPHERE"}}}}}}""",
            ))
            assertEquals(emptyList<String>(), messages)
            world(scene, messages, project).use { world ->
                assertEquals(1, messages.size)
                assertTrue(messages.single(), messages.single().contains("Flat (entity 0)") && messages.single().contains("one plane"))
                assertEquals(setOf(scene.named("Ball")), world.entities)
                run(world, 0.5f)
                assertTrue(scene.position("Ball").localPosition.y < 4f)
            }
        } finally {
            project.deleteRecursively()
        }
    }

    @Test
    fun convexHullFromAModelBuilds() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene()
        scene.named("Model 0").getComponent(ColliderComponent::class.java).shape = ColliderShape.CONVEX_HULL
        world(scene, messages).use { world ->
            assertEquals(emptyList<String>(), messages)
            assertTrue(scene.named("Model 0") in world.entities)
        }
    }

    @Test
    fun nonFiniteValueIsRefused() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene()
        scene.named("Model 0").getComponent(ColliderComponent::class.java).halfExtents.x = Float.NaN
        world(scene, messages).use { world ->
            val about = messages.filter { it.startsWith("Physics: Model 0 (entity 0)") }
            assertEquals(messages.toString(), 1, about.size)
            assertTrue(about.single(), about.single().contains("halfExtents") && about.single().contains("not finite"))
            assertFalse(scene.named("Model 0") in world.entities)
            assertTrue(scene.named("Terrain") in world.entities)
            world.advance(1f / 60f)
        }
    }

    @Test
    fun maxBelowMinConstraintIsLeftOut() {
        val messages = mutableListOf<String>()
        val scene = loadPhysicsScene()
        scene.named("Model 2").getComponent(ConstraintComponent::class.java).apply { minDistance = 2f; maxDistance = 1f }
        world(scene, messages).use { world ->
            assertEquals(1, messages.size)
            assertTrue(messages.single(), messages.single().contains("Model 2 (entity 2)") && messages.single().contains("maxDistance"))
            assertEquals(emptyList<PhysicsConstraint>(), world.constraints)
            run(world, 0.5f)
            assertTrue(scene.position("Model 0").localPosition.y < 3.086434f)
        }
    }

    @Test
    fun closedWorldRefusesUse() {
        val scene = loadPhysicsScene()
        val world = world(scene, mutableListOf())
        world.advance(1f / 60f)
        world.close()
        world.close()
        val error = assertThrows(IllegalStateException::class.java) { world.advance(1f / 60f) }
        assertEquals("physics world is closed", error.message)
        assertThrows(IllegalStateException::class.java) { world.step() }
        assertThrows(IllegalStateException::class.java) { world.bodyOf(scene.named("Model 0")) }
        assertThrows(IllegalStateException::class.java) { world.contacts() }
    }

    // ---- stepping ----

    @Test
    fun boxFallsOntoTheTerrain() {
        val scene = loadPhysicsScene()
        world(scene, mutableListOf()).use { world ->
            run(world, 5f)
            val position = scene.position("Model 0")
            assertTrue(position.localPosition.y < 3.086434f)
            val lowest = boxCorners(position.localPosition, position.localRotation).minOf { it.y }
            assertEquals("lowest point", 0f, lowest, 0.05f)
            val speed = world.bodyOf(scene.named("Model 0"))!!.velocity().len()
            assertTrue("speed $speed", speed < 0.01f)
        }
    }

    @Test
    fun staticTerrainStays() {
        val scene = loadPhysicsScene()
        world(scene, mutableListOf()).use { world ->
            run(world, 5f)
            assertEquals(Vector3(-38.25267f, 0f, -32.7754f), scene.position("Terrain").localPosition)
        }
    }

    @Test
    fun advanceRunsFixedStepsAndCarriesTheRemainder() {
        val scene = loadPhysicsScene()
        world(scene, mutableListOf()).use { world ->
            assertEquals(2, world.advance(1f / 60f))
            assertEquals(0, world.advance(1f / 240f))
            assertEquals(1, world.advance(1f / 240f))
            assertEquals(MAX_STEPS_PER_ADVANCE, world.advance(1f))
            assertEquals(1, world.advance(1f / 120f))
        }
    }

    @Test
    fun sameRunTwiceIsIdentical() {
        assertEquals(poses(2f), poses(2f))
    }

    @Test
    fun hundredWorldsInARowGiveTheSameResult() {
        val first = poses(0.5f)
        repeat(99) { assertEquals("run ${it + 2}", first, poses(0.5f)) }
        val scene = loadPhysicsScene()
        val world = world(scene, mutableListOf())
        world.close()
        assertThrows(IllegalStateException::class.java) { world.advance(1f / 60f) }
    }

    // ---- the game facade ----

    /** A scene of [entities] (`"<id>": {components}` pairs) in a project with no assets. */
    private fun sceneOf(entities: String): SceneContext = requireNotNull(
        physicsLoader(testProject("Physics"), failOnWarnings())
            .loadFromText("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{$entities}}}"""),
    )

    @Test
    fun constantThrustGivesTwentyMetresPerSecond() {
        val scene = sceneOf(""""0":{"components":{"NameComponent":{"name":"Plane"},
            "RigidBodyComponent":{"gravityFactor":0,"linearDamping":0},"ColliderComponent":{"shape":"SPHERE"}}}""")
        world(scene, mutableListOf()).use { world ->
            val plane = world.bodyOf(scene.named("Plane"))!!
            repeat(60) {
                plane.applyForce(Vector3(20f, 0f, 0f))
                world.advance(1f / 60f)
            }
            val v = plane.velocity()
            assertEquals(20f, v.x, 0.2f)
            assertEquals(0f, v.y, 1e-4f)
            assertEquals(20f * 0.5f, scene.position("Plane").localPosition.x, 0.2f)
        }
    }

    @Test
    fun aKinematicBodyMovesWhereTheGameMovesIt() {
        val scene = sceneOf(""""0":{"components":{"NameComponent":{"name":"Lift"},
            "RigidBodyComponent":{"motionType":"KINEMATIC"},"ColliderComponent":{}}}""")
        world(scene, mutableListOf()).use { world ->
            val lift = world.bodyOf(scene.named("Lift"))!!
            lift.moveKinematic(Vector3(0f, 2f, 0f), Quaternion(), 1f / 60f)
            world.advance(1f / 60f)
            assertEquals(2f, scene.position("Lift").localPosition.y, 1e-3f)
        }
    }

    @Test
    fun aGamePutsABodyBackAtRest() {
        val scene = sceneOf(""""0":{"components":{"NameComponent":{"name":"Plane"},
            "RigidBodyComponent":{"gravityFactor":0},"ColliderComponent":{"shape":"SPHERE"}}}""")
        world(scene, mutableListOf()).use { world ->
            val plane = world.bodyOf(scene.named("Plane"))!!
            plane.setVelocity(Vector3(5f, 0f, 0f))
            run(world, 0.5f)
            val turned = Quaternion(Vector3.Y, 90f)
            plane.setPose(Vector3(1f, 2f, 3f), turned)
            assertEquals(Vector3(1f, 2f, 3f), scene.position("Plane").localPosition)
            world.advance(1f / 60f)
            assertEquals(0f, plane.velocity().len(), 1e-4f)
            assertEquals(1f, scene.position("Plane").localPosition.x, 1e-4f)
            assertEquals(1f, kotlin.math.abs(turned.dot(scene.position("Plane").localRotation)), 1e-4f)
        }
    }

    @Test
    fun aGameAddsAndRemovesARope() {
        val scene = loadPhysicsScene()
        world(scene, mutableListOf()).use { world ->
            val rope = world.addRope(scene.named("Model 0"), Vector3(), null, Vector3(-3.035308f, 6f, -3.2570944f), 2f)
            assertEquals(2, world.constraints.size)
            world.remove(rope)
            world.remove(rope)
            assertEquals(1, world.constraints.size)
            assertThrows(IllegalArgumentException::class.java) {
                world.addRope(scene.named("Model 0"), Vector3(), null, Vector3(), -1f)
            }
        }
    }

    @Test
    fun removingAnEntityRemovesItsConstraints() {
        val scene = loadPhysicsScene()
        world(scene, mutableListOf()).use { world ->
            world.advance(1f / 60f)
            val model0 = scene.named("Model 0")
            scene.engine.removeEntity(model0)
            assertFalse(model0 in world.entities)
            assertEquals(emptyList<PhysicsConstraint>(), world.constraints)
            run(world, 0.5f)
            world.removeEntity(scene.named("Model 2"))
            assertEquals(setOf(scene.named("Terrain")), world.entities)
        }
    }

    @Test
    fun landingReportsAContactWithTheTerrain() {
        val scene = loadPhysicsScene()
        val model0 = scene.named("Model 0")
        val terrain = scene.named("Terrain")
        world(scene, mutableListOf()).use { world ->
            var landing: Contact? = null
            var steps = 0
            while (landing == null && steps < 5 * 120) {
                world.step()
                steps++
                landing = world.contacts().firstOrNull { setOf(it.a, it.b) == setOf(model0, terrain) }
            }
            assertNotNull("no contact with the terrain in 5 s", landing)
            assertTrue(landing!!.first)
            assertTrue("relative speed ${landing.relativeSpeed}", landing.relativeSpeed > 0f)
        }
    }

    /** Every entity's position and rotation after simulating the fixture for [seconds]. */
    private fun poses(seconds: Float): Map<Int, List<Float>> {
        val scene = loadPhysicsScene()
        world(scene, mutableListOf()).use { run(it, seconds) }
        return scene.engine.ids.ids.associateWith { id ->
            val p = scene.engine.ids[id]!!.getComponent(PositionComponent::class.java) ?: return@associateWith emptyList()
            listOf(p.localPosition.x, p.localPosition.y, p.localPosition.z, p.localRotation.x, p.localRotation.y, p.localRotation.z, p.localRotation.w)
        }
    }

    private fun boxCorners(center: Vector3, rotation: Quaternion): List<Vector3> =
        listOf(-0.5f, 0.5f).flatMap { x -> listOf(-0.5f, 0.5f).flatMap { y -> listOf(-0.5f, 0.5f).map { z ->
            rotation.transform(Vector3(x, y, z)).add(center)
        } } }

    /** A model asset whose four points lie in the plane y = 0: a quad. */
    private fun writeFlatModel(dir: File) {
        dir.mkdirs()
        val buffer = ByteBuffer.allocate(48 + 12).order(ByteOrder.LITTLE_ENDIAN)
        for (p in listOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f)) buffer.putFloat(p)
        for (i in listOf(0, 1, 2, 0, 2, 3)) buffer.putShort(i.toShort())
        val data = Base64.getEncoder().encodeToString(buffer.array())
        File(dir, "model.gltf").writeText(
            """{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
            "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1}]}],
            "buffers":[{"byteLength":60,"uri":"data:application/octet-stream;base64,$data"}],
            "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":48},{"buffer":0,"byteOffset":48,"byteLength":12}],
            "accessors":[{"bufferView":0,"componentType":5126,"count":4,"type":"VEC3","min":[0,0,0],"max":[1,0,1]},
                         {"bufferView":1,"componentType":5123,"count":6,"type":"SCALAR"}]}""",
        )
        File(dir, "meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"6f1c1b0e-5d7a-4e2b-9a43-0c2d8e1f7a10","type":"MODEL","additional":{"file":"model.gltf","format":"GLTF","binary":false,"materials":[]}}""",
        )
        assertNotNull(File(dir, "model.gltf"))
    }
}
