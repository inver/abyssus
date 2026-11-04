/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.dto

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

/** A project file read into [ProjectDto] and printed again keeps what the editor edits. */
class ProjectDtoFileTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val text =
        """{"format":"abyssus","formatVersion":1,"mainCamera":{"viewPointPosition":{"x":0.6,"y":-0.25,"z":-0.75},
"position":{"x":-6.0,"y":4.0,"z":10.0},"far":500.0,"near":0.1,"fieldOfView":67.0},""" +
                """"settings":{"export":{"jsonType":"json","compressScenes":false}},"activeSceneName":"Field",""" +
                """"selectedCamera":-1,
"physicsEnabled":true,"sceneViewAntialiasing":"MSAA_4","uiAssetNames":["screens"],"name":"P"}"""

    @Test
    fun theFileKeysBind() {
        val project = json.parse(text, ProjectDto::class.java)
        assertEquals("P", project.name)
        assertEquals("Field", project.activeSceneName)
        assertEquals(true, project.physicsEnabled)
        assertEquals("MSAA_4", project.sceneViewAntialiasing)
        assertEquals(500f, project.mainCamera!!.far!!, 0f)
        assertEquals(listOf("screens"), project.uiAssetNames)
        assertEquals("json", project.settings!!["export"]["jsonType"].asText())
    }

    @Test
    fun printingKeepsEveryKeyAndWritesNoEmptyLists() {
        val printed = json.pretty(json.parse(text, ProjectDto::class.java))
        val again = json.parse(printed, ProjectDto::class.java)
        assertEquals(json.parse(text, ProjectDto::class.java), again)
        assertFalse(printed, printed.contains("\"scenes\""))
        assertTrue(printed.contains("\"formatVersion\": 1"))
    }

    @Test
    fun anAbsentKeyStaysAbsent() {
        val project = json.parse("""{"format":"abyssus","formatVersion":1,"name":"Q"}""", ProjectDto::class.java)
        assertNull(project.physicsEnabled)
        val printed = json.pretty(project)
        assertFalse(printed, printed.contains("physicsEnabled"))
        assertFalse(printed, printed.contains("uiAssetNames"))
    }
}
