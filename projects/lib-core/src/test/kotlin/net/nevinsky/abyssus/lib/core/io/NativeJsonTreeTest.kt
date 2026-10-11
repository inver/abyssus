/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.io

import com.fasterxml.jackson.databind.json.JsonMapper
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File

class NativeJsonTreeTest {
    private val tree = NativeJsonTree()
    private val compact = JsonMapper()

    @Test
    fun keepsNumberTextKeyOrderAndNulls() {
        val text = """{"z":2.50,"a":-0.0,"n":{"e":1.0E-4,"k":null,"i":7,"big":12345678901234567890},"l":[1.0,"s",true]}"""
        assertEquals(text, compact.writeValueAsString(tree.parse(text)))
    }

    @Test
    fun rejectsEmptyTrailingAndNonObjectRoots() {
        assertThrows(IllegalArgumentException::class.java) { tree.parse("") }
        assertThrows(IllegalArgumentException::class.java) { tree.parse("{} {}") }
        assertThrows(IllegalArgumentException::class.java) { tree.parseObject("[1]") }
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException::class.java) { tree.parse("""{"a":""") }
    }

    @Test
    fun theRuntimeSceneLoaderKeepsTheEcsBlockAsRead() {
        val text = """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"1":{"components":{"X":{"w":2.50}}}}}}"""
        val scene: SceneDto = SceneLoader(JsonProcessor(NOPLogger.NOP_LOGGER), FileLoader(File("."))).parse(text)
        assertTrue(compact.writeValueAsString(scene.ecs).contains("2.50"))
    }
}
