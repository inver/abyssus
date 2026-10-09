/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.testing.failOnWarnings
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.runtime.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.runtime.SceneContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RopeTensionTest {
    /** A 1 kg sphere at (0, 8, 0), and [rope] as its constraint (none when empty). */
    private fun weight(rope: String = ""): SceneContext = requireNotNull(
        physicsLoader(testProject("Physics"), failOnWarnings()).loadFromText(
            """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"0":{"components":{"NameComponent":{"name":"Weight"},
            "PositionComponent":{"localPosition":{"y":8}},"RigidBodyComponent":{},"ColliderComponent":{"shape":"SPHERE","radius":0.2}
            ${if (rope.isEmpty()) "" else ",\"ConstraintComponent\":$rope"}}}}}}""",
        ),
    )

    private fun world(scene: SceneContext) =
        PhysicsWorld(scene.engine, PhysicsAssets(testProject("Physics")), failOnWarnings(), JoltNatives())

    private fun distance(rope: PhysicsConstraint): Float {
        val a = Vector3()
        val b = Vector3()
        rope.anchors(a, b)
        return a.dst(b)
    }

    @Test
    fun hangingWeightReadsItsWeight() {
        val scene = weight("""{"otherAnchor":{"y":10},"maxDistance":2}""")
        world(scene).use { world ->
            repeat(120) { world.advance(1f / 60f) }
            val tension = world.constraints.single().tension
            assertTrue("tension $tension N", tension in 9.32f..10.30f)
        }
    }

    @Test
    fun slackRopeReadsZero() {
        val scene = weight("""{"otherAnchor":{"y":10},"maxDistance":2}""")
        world(scene).use { world ->
            val rope = world.constraints.single()
            repeat(60) { world.advance(1f / 60f) }
            world.bodyOf(scene.named("Weight"))!!.setVelocity(Vector3(0f, 3f, 0f))
            var slackSteps = 0
            var tautAgain = false
            repeat(240) {
                world.step()
                if (distance(rope) < 2f - 0.001f) {
                    slackSteps++
                    assertEquals("tension while slack", 0f, rope.tension, 0f)
                } else if (slackSteps > 0 && rope.tension > 0f) {
                    tautAgain = true
                }
            }
            assertTrue("the rope never went slack", slackSteps > 10)
            assertTrue("the rope never pulled again", tautAgain)
        }
    }

    @Test
    fun twoRopesShareTheLoad() {
        val scene = weight()
        world(scene).use { world ->
            val weight = scene.named("Weight")
            val left = world.addRope(weight, Vector3(-0.1f, 0f, 0f), null, Vector3(-0.1f, 10f, 0f), 2f)
            val right = world.addRope(weight, Vector3(0.1f, 0f, 0f), null, Vector3(0.1f, 10f, 0f), 2f)
            repeat(120) { world.advance(1f / 60f) }
            for (rope in listOf(left, right)) assertTrue("tension ${rope.tension} N", rope.tension in 4.66f..5.15f)
            assertEquals(9.81f, left.tension + right.tension, 0.49f)
        }
    }
}
