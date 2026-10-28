/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SceneEntityTreeTest {
    private val nodes = JsonNodeFactory.instance

    private fun components(vararg names: String): ObjectNode =
        nodes.objectNode().also { c -> names.forEach { c.putObject(it) } }

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
    fun aNewEntityHasComponentsAndNoFlags() {
        val root = SceneJson().parse(File("src/test/testData/project/Lights/scenes/Creation Baseline.scene").readText())
        val id = SceneEntityTree(root).insert { components("NameComponent", "CameraComponent") }!!
        assertEquals(listOf("components"), root["ecs"][id].fieldNames().asSequence().toList())
    }

    @Test
    fun aNonNativeSceneTakesNoEntity() {
        assertNull(SceneEntityTree(SceneJson().parse("""{"ecs":{}}""")).insert { components("NameComponent") })
    }
}
