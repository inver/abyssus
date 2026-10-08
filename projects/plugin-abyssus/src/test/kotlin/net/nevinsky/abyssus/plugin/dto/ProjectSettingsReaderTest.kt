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