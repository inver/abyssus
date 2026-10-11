/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerBake
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.scene.SceneContext
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.CAPSULE
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.box
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.layerJson
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.scene
import net.nevinsky.abyssus.lib.physics.jolt.FoliageFailure
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Foliage bodies are released on owner removal, on a failed layer and on close, and the caps refuse before Jolt. */
class PhysicsFoliageLifecycleTest {
    private val project = FoliageProject()
    private val natives = JoltNatives()
    private val messages = mutableListOf<String>()

    @After
    fun cleanUp() = project.close()

    private fun load(text: String): SceneContext =
        requireNotNull(physicsLoader(project.dir, warningsTo(messages)).loadFromText(text)) { messages.joinToString("\n") }

    private fun world(scene: SceneContext, failure: FoliageFailure? = null) =
        PhysicsWorld(scene.engine, PhysicsAssets(project.dir), warningsTo(messages), natives, failure)

    /** A 2 by 2 grid of chunks, each with one trunk well inside its own corner. */
    private fun grid(id: Int = 1) = FoliageLayerBake(
        id, 1, 2, 2,
        listOf(
            listOf(FoliageCopy(0, 20f, 20f, 0f, 1f)), listOf(FoliageCopy(0, 70f, 20f, 0f, 1f)),
            listOf(FoliageCopy(0, 20f, 70f, 0f, 1f)), listOf(FoliageCopy(0, 70f, 70f, 0f, 1f)),
        ),
    )

    private fun heightOfBox(scene: SceneContext) = scene.named("Box").getComponent(PositionComponent::class.java).localPosition.y

    @Test
    fun removingTheOwnerRemovesEveryChunkAndLeavesOtherBodiesValid() {
        project.foliage("[${layerJson(CAPSULE)}]")
        project.bake(listOf(grid()))
        val scene = load(scene(extra = box(20f, 5f, 20f)))
        world(scene).use { world ->
            assertEquals(4, world.foliageBodies)
            repeat(180) { world.advance(1f / 60f) }
            assertEquals(2.0f, heightOfBox(scene), 0.05f)
            // the owner has foliage but no ordinary body: removing it must still release the chunks
            scene.engine.removeEntity(scene.named("Ground"))
            assertEquals(0, world.foliageBodies)
            repeat(180) { world.advance(1f / 60f) }
            assertEquals(-18.5f, heightOfBox(scene), 0.05f)
            assertTrue(scene.named("Floor") in world.entities && scene.named("Box") in world.entities)
        }
    }

    @Test
    fun aLayerJoltRefusesIsRolledBackWhileTheNextOneIsKept() {
        project.foliage("[${layerJson(CAPSULE, id = 1)},${layerJson(CAPSULE, id = 2)}]")
        project.bake(listOf(grid(1), grid(2)))
        val scene = load(scene(extra = box(20f, 5f, 20f)))
        world(scene) { layerId, chunk -> if (layerId == 1 && chunk == 3) "injected failure" else null }.use { world ->
            // layer 1 built two chunks and was rolled back; layer 2 built all four
            assertEquals(4, world.foliageBodies)
            assertEquals(1, messages.count { it.contains("layer 1 does not collide: injected failure") })
            repeat(180) { world.advance(1f / 60f) }
            assertEquals(2.0f, heightOfBox(scene), 0.05f)
        }
    }

    @Test
    fun aHundredWorldsCreateStepAndCloseAndAClosedWorldRefusesOperations() {
        project.foliage("[${layerJson(CAPSULE)}]")
        project.bake(listOf(grid()))
        var last: PhysicsWorld? = null
        repeat(100) {
            val scene = load(scene(extra = box(20f, 5f, 20f)))
            val world = world(scene)
            world.advance(1f / 30f)
            assertEquals(4, world.foliageBodies)
            world.close()
            world.close()
            last = world
        }
        val closed = last!!
        assertThrows(IllegalStateException::class.java) { closed.foliageBodies }
        assertThrows(IllegalStateException::class.java) { closed.advance(0.1f) }
        assertThrows(IllegalStateException::class.java) { closed.entities }
    }

    @Test
    fun aBakeOf300000CopiesIsRefusedBeforeAnyBodyIsMade() {
        project.foliage("[${layerJson(CAPSULE)}]")
        val dense = List(300_000) { FoliageCopy(0, 1f + it % 90, 1f + (it / 90) % 90, 0f, 1f) }
        project.bake(listOf(FoliageProject.layer(dense)))
        val scene = load(scene(extra = box(20f, 5f, 20f)))
        val start = System.nanoTime()
        world(scene).use { world ->
            assertEquals(0, world.foliageBodies)
        }
        assertTrue("refusal took ${(System.nanoTime() - start) / 1_000_000} ms", System.nanoTime() - start < 20_000_000_000L)
        assertEquals(1, messages.count { it.contains("over the limit of 200000") })
    }

    @Test
    fun twoLayersOf150000AdmitTheFirstAndRefuseTheSecondInLayerOrder() {
        project.foliage("[${layerJson(CAPSULE, id = 1)},${layerJson(CAPSULE, id = 2)}]")
        fun dense(id: Int) = FoliageLayerBake(
            id, 1, 10, 10,
            List(100) { chunk -> List(1_500) { i -> FoliageCopy(0, (chunk % 10) * 10f + 0.5f + (i % 30) * 0.3f, (chunk / 10) * 10f + 0.5f + (i / 30) * 0.15f, 0f, 0.1f) } },
        )
        project.bake(listOf(dense(1), dense(2)))
        val scene = load(scene(extra = box(5f, 5f, 5f)))
        world(scene).use { world ->
            assertEquals(100, world.foliageBodies)
            assertEquals(1, messages.count { it.contains("layer 2") && it.contains("limit of 200000") })
        }
    }
}
