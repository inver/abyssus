/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.filetype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneJsonTest {
    private val minified = """{"id":0,"name":"Main <Scene> & 'co'","fog":{"density":0.001,"gradient":1.5,"color":{"r":1.0,"a":1.0}},"list":[1,2.50,{"x":-0.0}],"empty":{},"none":[]}"""

    @Test
    fun breaksMinifiedJsonIntoIndentedLines() {
        val pretty = SceneJson.pretty(minified)!!
        assertTrue(pretty.lines().size > 10)
        assertTrue(pretty, pretty.contains("\n  \"fog\": {\n    \"density\": 0.001,"))
        assertTrue(pretty.endsWith("}\n"))
    }

    @Test
    fun keepsKeyOrderAndNumberTextAndDoesNotEscapeHtml() {
        val pretty = SceneJson.pretty(minified)!!
        assertTrue(pretty.indexOf("\"id\"") < pretty.indexOf("\"name\""))
        assertTrue(pretty.indexOf("\"density\"") < pretty.indexOf("\"gradient\""))
        assertTrue(pretty, pretty.contains("\"r\": 1.0"))
        assertTrue(pretty, pretty.contains("2.50"))
        assertTrue(pretty, pretty.contains("-0.0"))
        assertTrue(pretty, pretty.contains("Main <Scene> & 'co'"))
    }

    @Test
    fun isIdempotent() {
        val once = SceneJson.pretty(minified)!!
        assertEquals(once, SceneJson.pretty(once))
    }

    @Test
    fun roundTripsToTheSameDocument() {
        assertEquals(SceneJson.parse(minified), SceneJson.parse(SceneJson.pretty(minified)!!))
    }

    @Test
    fun invalidOrNonObjectJsonIsLeftAlone() {
        assertNull(SceneJson.pretty("{ nope"))
        assertNull(SceneJson.pretty(""))
        assertNull(SceneJson.pretty("[1,2]"))
        assertNull(SceneJson.pretty("42"))
        assertNotNull(SceneJson.pretty("{}"))
    }

    @Test
    fun keepsNullMembersAtEveryLevel() {
        val withNulls = """{"skyboxName":null,"fog":{"gradient":null,"color":{"r":1}},"list":[null,{"a":null}]}"""
        val pretty = SceneJson.pretty(withNulls)!!
        assertTrue(pretty, pretty.contains("\"skyboxName\": null"))
        assertEquals(SceneJson.parse(withNulls), SceneJson.parse(pretty))
    }

    @Test
    fun compactKeepsNullsAndStaysOnOneLine() {
        val element = SceneJson.parse("""{"a":null,"b":{"c":null},"d":"<x>"}""")
        assertEquals("""{"a":null,"b":{"c":null},"d":"<x>"}""", SceneJson.compact(element))
    }
}
