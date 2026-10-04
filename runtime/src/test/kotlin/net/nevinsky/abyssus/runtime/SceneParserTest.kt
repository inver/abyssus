/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime

import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.runtime.scene.SceneParser
import org.junit.Assert.*
import org.junit.Test

class SceneParserTest {
    private val parser = SceneParser(JsonProcessor())
    @Test fun mainSceneTopLevelFieldsAreRead() {
        val scene = parser.parse(testProject("Untitled").resolve("scenes/Main Scene.scene").readText())
        assertEquals("Ololo", scene.name)
        assertEquals("skybox_physical", scene.skyboxName)
        assertEquals(9, scene.ecs!!["entities"].size())
    }
    @Test fun nonJsonFailsWithAMessage() {
        val error = assertThrows(Exception::class.java) { parser.parse("not JSON") }
        assertFalse(error.message.isNullOrBlank())
    }
}
