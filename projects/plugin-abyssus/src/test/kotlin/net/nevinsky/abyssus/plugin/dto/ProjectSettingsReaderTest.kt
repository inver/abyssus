/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import org.junit.Assert.*
import org.junit.Test

class ProjectSettingsReaderTest {

    private val reader = ProjectSettingsReader()

    @Test
    fun committedProjectsDeclareTheirPhysicsChoice() {
        val fixtures = java.io.File("src/test/testData/project")
        assertFalse(reader.read(java.io.File(fixtures, "Untitled/Untitled.abss").readText()).physicsEnabled)
        assertTrue(reader.read(java.io.File(fixtures, "Physics/Physics.abss").readText()).physicsEnabled)
        assertTrue(reader.read(java.io.File("../app-game-control-line/project/ControlLine/ControlLine.abss").readText()).physicsEnabled)
    }

    @Test
    fun untitledAbssIsOffWithNoProblems() {
        val text = """{"format":"abyssus","formatVersion":1,"name":"Untitled"}"""
        val result = reader.read(text)
        assertFalse(result.physicsEnabled)
        assertTrue(result.problems.isEmpty())
    }

    @Test
    fun physicsAbssWithKeyIsOn() {
        val text = """{"format":"abyssus","formatVersion":1,"name":"Physics","physicsEnabled":true}"""
        val result = reader.read(text)
        assertTrue(result.physicsEnabled)
        assertTrue(result.problems.isEmpty())
    }

    @Test
    fun nonBooleanPhysicsEnabledIsOffWithProblem() {
        val text = """{"format":"abyssus","formatVersion":1,"name":"Test","physicsEnabled":"yes"}"""
        val result = reader.read(text)
        assertFalse(result.physicsEnabled)
        assertEquals(listOf("physicsEnabled"), result.problems)
    }

    @Test
    fun unsupportedFormatVersionIsOff() {
        val text = """{"format":"abyssus","formatVersion":2,"name":"Test"}"""
        val result = reader.read(text)
        assertFalse(result.physicsEnabled)
        assertFalse(result.problems.isEmpty())
    }

    @Test
    fun missingFormatMarkerIsOff() {
        val text = """{"name":"Test"}"""
        val result = reader.read(text)
        assertFalse(result.physicsEnabled)
        assertFalse(result.problems.isEmpty())
    }
}
