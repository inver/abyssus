/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.app.game.controlline.tools

import com.badlogic.gdx.math.Vector3
import java.io.File
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

/** The leadout migration (`FieldLeadouts`): dummies at the old values, the members removed, a rerun unchanged. */
class FieldLeadoutsMigrationTest {
    private val tool = FieldLeadoutsTool()
    private val registry = ComponentRegistry()
    private val parser = SceneParser(JsonProcessor(NOPLogger.NOP_LOGGER), registry)

    /** The leadouts each plane of the field flew with before the migration (`PlaneComponent` values). */
    private val flown = mapOf(
        "Racer" to (Vector3(0.33f, 0f, 0.01f) to Vector3(0.33f, 0f, -0.03f)),
        "Stunter" to (Vector3(0.75f, 0f, 0.02f) to Vector3(0.75f, 0f, -0.04f)),
        "Trainer" to (Vector3(0.48f, 0f, 0.02f) to Vector3(0.48f, 0f, -0.04f)),
        "Mig-3" to (Vector3(0.485f, 0f, 0.02f) to Vector3(0.485f, 0f, -0.04f)),
    )

    private fun planes(scene: SceneDto): Map<Long, String> =
        scene.ecs.filterValues { e ->
            e.component(RawComponentsWrapper::class.java)?.entries?.any { it.name.endsWith("PlaneComponent") } == true
        }.mapValues { (_, e) -> e.component(NameComponent::class.java)!!.name!! }

    private fun dummies(scene: SceneDto, parent: Long): Map<String, Vector3> =
        scene.ecs.values.filter {
            it.component(HeaderComponent::class.java)?.type == EntityType.DUMMY &&
                it.component(ParentComponent::class.java)?.parentEntityId?.toLong() == parent
        }.associate {
            val name = it.component(NameComponent::class.java)!!.name!!
            name to it.component(PositionComponent::class.java)!!.localPosition
        }

    private fun planePayload(scene: SceneDto, id: Long): String =
        scene.entity(id)!!.component(RawComponentsWrapper::class.java)!!.entries
            .single { it.name.endsWith("PlaneComponent") }
            .json

    private fun assertMigrated(scene: SceneDto, expectedPlanes: Set<String>) {
        val planes = planes(scene)
        assertEquals(expectedPlanes, planes.values.toSet())
        for ((id, name) in planes) {
            val (up, down) = flown.getValue(name)
            assertEquals(name, mapOf("leadoutUp" to up, "leadoutDown" to down), dummies(scene, id))
            val payload = planePayload(scene, id)
            assertFalse(payload, "leadout" in payload)
        }
    }

    @Test
    fun aPlaneGetsItsLeadoutsAsDummiesAndLosesTheMembers() {
        val text = """{"formatVersion":1,"name":"S","ecs":{
            "5":{"type":"OBJECT","components":{"NameComponent":{"name":"Trainer"},"PositionComponent":{},
              "PlaneComponent":{"lineLength":15,"leadoutUp":{"x":0.48,"y":0,"z":0.02},
                "leadoutDown":{"x":0.48,"y":0,"z":-0.04},"thrust":5.5,"zeroLiftDrag":0.06}}}}}"""
        val migrated = tool.migrate(text)!!
        val scene = parser.parse(migrated)
        assertMigrated(scene, setOf("Trainer"))
        // the other members keep their values and order
        val payload = planePayload(scene, 5)
        assertTrue(payload, Regex("lineLength.*15.*thrust.*5\\.5.*zeroLiftDrag.*0\\.06").containsMatchIn(payload))
        // the dummies are the plane's children, the next ids, in order
        assertEquals(listOf(6L, 7L), scene.ecs.keys.filter { it > 5 }.sorted())
        assertNull("a rerun changes nothing", tool.migrate(migrated))
    }

    @Test
    fun theCommittedFieldIsMigratedAndARerunChangesNothing() {
        val text = File(committedProject(), "scenes/Field.scene").readText()
        assertMigrated(parser.parse(text), flown.keys)
        assertNull(tool.migrate(text))
    }

    @Test
    fun theOriginalFixtureMigratesAndARerunChangesNothing() {
        // the fixture is the pinned input of the foliage migration (its hash is in the foliage manifest), so it is
        // migrated here in memory, not on disk
        val migrated = tool.migrate(originalScene().readText())!!
        assertMigrated(parser.parse(migrated), setOf("Racer", "Stunter", "Trainer"))
        assertNull(tool.migrate(migrated))
    }
}
