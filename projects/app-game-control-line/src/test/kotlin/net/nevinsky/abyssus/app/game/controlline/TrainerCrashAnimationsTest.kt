/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.physics.PhysicsAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trainer's crash clips (`importers/TrainerCrashAnimations.kt`), as the game's Assimp loader reads them. */
class TrainerCrashAnimationsTest {
    private val data = AssimpModelLoader().loadData(FileHandle(bundledProject().resolve("assets/model_trainer/model.glb").toFile()))

    private fun nodes(): Map<String, ModelNode> {
        val out = HashMap<String, ModelNode>()
        fun visit(node: ModelNode) {
            out[node.id] = node
            node.children.orEmpty().filterNotNull().forEach(::visit)
        }
        data.nodes.forEach(::visit)
        return out
    }

    @Test
    fun theTrainerHasTheThreeCrashClips() {
        assertEquals(setOf("crash_little", "crash_medium", "crash_full"), data.animations.map { it.id }.toSet())
    }

    @Test
    fun eachClipMovesTheBrokenParts() {
        val moved = data.animations.associate { a -> a.id to a.nodeAnimations.map { it.nodeId }.toSet() }
        assertTrue(moved.getValue("crash_little").containsAll(setOf("NoseWheel", "LeftWheel", "RightWheel")))
        assertTrue(moved.getValue("crash_little").none { it.contains("Wing") || it == "Tail" })
        assertTrue(moved.getValue("crash_medium").containsAll(setOf("LeftWing", "Tail")))
        assertTrue(moved.getValue("crash_medium").none { it.endsWith("Wheel") || it == "RightWing" })
        assertTrue(moved.getValue("crash_full").containsAll(
            setOf("Trainer", "Propeller", "NoseWheel", "LeftWheel", "RightWheel", "LeftWing", "RightWing", "Tail")))
    }

    @Test
    fun everyClipStartsFromTheRestPose() {
        val nodes = nodes()
        for (animation in data.animations) for (channel in animation.nodeAnimations) {
            val node = checkNotNull(nodes[channel.nodeId]) { "${animation.id}: no node ${channel.nodeId}" }
            val start = channel.translation.first()
            assertEquals("${animation.id}/${channel.nodeId}", 0f, start.keytime, 0f)
            assertTrue("${animation.id}/${channel.nodeId}: ${start.value}", start.value.epsilonEquals(node.translation ?: Vector3.Zero, 1e-5f))
            assertTrue("${animation.id}/${channel.nodeId}: ${channel.rotation.first().value}",
                channel.rotation.first().value.isIdentity(1e-5f))
        }
    }

    @Test
    fun theRestPoseKeepsTheTrainersSizeAndWheels() {
        val points = checkNotNull(PhysicsAssets(bundledProject().toFile()).modelPoints("model_trainer"))
        assertEquals(-0.136f, points.minOf { it.y }, 1e-3f)
        assertEquals(1.0f, points.maxOf { it.x } - points.minOf { it.x }, 2e-3f)
    }
}
