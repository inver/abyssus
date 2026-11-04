/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import java.io.File
import net.nevinsky.abyssus.lib.core.dto.entityWriter
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

/** Every fixture scene survives parse, print, parse: the printed text is a fixed point of the serializer. */
class SceneRoundTripTest {
    private val registry = ComponentRegistry()
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER, listOf(entityWriter(registry)))
    private val parser = SceneParser(json, registry)

    private val scenes =
        File(System.getProperty("abyssus.testData"), "project")
            .walkTopDown()
            .filter { it.extension == "scene" }
            .sortedBy { it.path }
            .toList()

    @Test fun fixtureScenesExist() = assertTrue(scenes.size >= 5)

    @Test
    fun printedScenesParseAndPrintTheSame() {
        for (file in scenes) {
            val first = json.pretty(parser.parse(file.readText()))
            val second = json.pretty(parser.parse(first))
            assertEquals(file.path, first, second)
        }
    }

    @Test
    fun entityAndComponentCountsSurvive() {
        for (file in scenes) {
            val before = parser.parse(file.readText())
            val after = parser.parse(json.pretty(before))
            assertEquals(file.path, before.ecs.keys, after.ecs.keys)
            for ((id, entity) in before.ecs) {
                assertEquals(
                    "${file.path} #$id",
                    entity.components.map { it::class },
                    after.ecs.getValue(id).components.map { it::class },
                )
            }
        }
    }
}
