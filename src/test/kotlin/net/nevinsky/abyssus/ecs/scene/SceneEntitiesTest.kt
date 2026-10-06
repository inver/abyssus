/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.ecs.scene

import net.nevinsky.abyssus.editor.document.SceneEntityTree
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.editor.document.SceneJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SceneEntitiesTest {
    private val nodes = JsonNodeFactory.instance

    private fun components(vararg names: String): ObjectNode =
        nodes.objectNode().also { c -> names.forEach { c.putObject(it) } }

    private fun wrapped() = SceneJson().parse(File("src/test/testData/project/Lights/scenes/Creation Baseline.scene").readText())

    @Test
    fun theNextIdIsOneAboveTheHighestAndAnEmptySceneStartsAtZero() {
        val main = SceneJson().parse(File("src/test/testData/project/Tree/scenes/Main Scene.scene").readText())
        assertEquals("9", SceneEntityTree(main).insert { components("NameComponent") })
        assertTrue(main["ecs"]["9"]["components"].has("NameComponent"))
        val empty = SceneJson().parse("""{"format":"abyssus","formatVersion":1}""")
        assertEquals("0", SceneEntityTree(empty).insert { components("NameComponent") })
        assertTrue(empty["ecs"]["0"].isObject)
    }

    @Test
    fun aWrappedSceneReusesOrAddsArchetypes() {
        val root = wrapped()
        val before = root["ecs"]["archetypes"].deepCopy<JsonNode>()
        val first = SceneEntityTree(root).insert { components("NameComponent", "CameraComponent") }!!
        val archetypes = root["ecs"]["archetypes"]
        val added = root["ecs"]["entities"][first]["archetype"].asText()
        assertNull("a new archetype for a new set of components", before[added])
        assertEquals(setOf("NameComponent", "CameraComponent"), archetypes[added].map { it.asText() }.toSet())
        val second = SceneEntityTree(root).insert { components("CameraComponent", "NameComponent") }!!
        assertEquals(added, root["ecs"]["entities"][second]["archetype"].asText())
        for ((key, value) in before.properties()) assertEquals(value, archetypes[key])
    }

    @Test
    fun matchArchetypeFollowsTheEntitysComponents() {
        val root = wrapped()
        val id = SceneEntityTree(root).insert { components("NameComponent") }!!
        val components = root["ecs"]["entities"][id]["components"] as ObjectNode
        components.putObject("CameraComponent")
        assertTrue(SceneEntityTree(root).matchArchetype(id))
        val archetype = root["ecs"]["archetypes"][root["ecs"]["entities"][id]["archetype"].asText()]
        assertEquals(setOf("NameComponent", "CameraComponent"), archetype.map { it.asText() }.toSet())
        val native = SceneJson().parse("""{"format":"abyssus","formatVersion":1,"ecs":{"0":{"components":{}}}}""")
        assertTrue(SceneEntityTree(native).matchArchetype("0"))
    }

    @Test
    fun aNonNativeSceneTakesNoEntity() {
        assertNull(SceneEntityTree(SceneJson().parse("""{"ecs":{}}""")).insert { components("NameComponent") })
    }
}
