/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.dto.entityWriter
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class SceneSettingEditTest {
    private val registry = ComponentRegistry()
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER, listOf(entityWriter(registry)))
    private val parser = SceneParser(json, registry)
    private val editor = SceneTextEditor(parser, json)
    private val text = """{"formatVersion":1,"name":"A","ambientLightEnabled":true,"ecs":{}}"""

    @Test
    fun aChannelOfAMissingAmbientLightIsWrittenAndReadsBack() {
        val outcome = editor.replace(text) { it.withSettingValue("ambientLight.color.r", "0.5") }
        val written = (outcome as TextEditOutcome.Edited).text
        println(written)
        assertEquals(0.5f, parser.parse(written).ambientLight?.color?.r)
    }

    @Test
    fun textThatIsNotANumberIsDeclined() {
        assertNull(parser.parse(text).withSettingValue("ambientLight.color.r", "x"))
    }
}
