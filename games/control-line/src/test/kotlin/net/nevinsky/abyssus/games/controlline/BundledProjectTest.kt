/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline

import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.games.controlline.components.PilotComponent
import net.nevinsky.abyssus.games.controlline.components.PlaneComponent
import net.nevinsky.abyssus.physics.ColliderComponent
import net.nevinsky.abyssus.physics.ColliderShape
import net.nevinsky.abyssus.physics.MotionType
import net.nevinsky.abyssus.physics.RigidBodyComponent
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import org.junit.Assert.assertEquals
import org.junit.Test

class BundledProjectTest {
    @Test
    fun theFieldLoadsWithNoWarnings() {
        val messages = mutableListOf<String>()
        val scene = loadField(messages)
        assertEquals(emptyList<String>(), messages)

        val planes = scene.engine.entities.filter { it.getComponent(PlaneComponent::class.java) != null }
        assertEquals(listOf("Racer", "Stunter", "Trainer"), planes.map { it.getComponent(NameComponent::class.java).name.orEmpty() }.sorted())
        for (plane in planes) {
            assertEquals(MotionType.DYNAMIC, plane.getComponent(RigidBodyComponent::class.java).motionType)
            assertEquals(ColliderShape.CONVEX_HULL, plane.getComponent(ColliderComponent::class.java).shape)
        }
        assertEquals(1, scene.engine.entities.count { it.getComponent(PilotComponent::class.java) != null })
        assertEquals(ColliderShape.HEIGHT_FIELD, scene.named("Field").getComponent(ColliderComponent::class.java).shape)
    }

    @Test
    fun physicsBuildsEveryBodyWithNoWarnings() {
        val messages = mutableListOf<String>()
        val scene = loadField(messages)
        fieldWorld(scene, messages).use { world ->
            assertEquals(setOf("Field", "Pilot", "Racer", "Stunter", "Trainer"),
                world.entities.map { it.getComponent(NameComponent::class.java).name.orEmpty() }.toSet())
        }
        assertEquals(emptyList<String>(), messages)
    }

    @Test
    fun parkedPlanesRestOnTheGround() {
        val scene = loadField()
        val before = listOf("Racer", "Stunter", "Trainer").associateWith { scene.named(it).position().localPosition.cpy() }
        fieldWorld(scene).use { world -> repeat(120) { world.advance(1f / 60f) } }
        for ((name, start) in before) {
            val end = scene.named(name).position().localPosition
            assertEquals("$name moved: $start -> $end", 0f, end.dst(start), 0.02f)
        }
    }

    private fun Entity.position() = getComponent(PositionComponent::class.java)
}
