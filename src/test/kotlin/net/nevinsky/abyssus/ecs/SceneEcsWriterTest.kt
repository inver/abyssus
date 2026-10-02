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

package net.nevinsky.abyssus.ecs

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.ecs.scene.SceneEcsWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SceneEcsWriterTest {
    /** Numbers by value, objects by key regardless of order, so `100` and `100.0` or key order do not matter. */
    private fun assertSameJson(path: String, expected: JsonNode, actual: JsonNode) {
        when {
            expected.isObject -> {
                // the writer leaves out a zero number (a default), which Mundus may have written out explicitly
                val kept = expected.fieldNames().asSequence().filter { k -> actual.has(k) || !(expected[k].isNumber && expected[k].doubleValue() == 0.0) }.toSet()
                assertEquals("keys at $path", kept, actual.fieldNames().asSequence().toSet())
                kept.forEach { k -> assertSameJson("$path/$k", expected[k], actual[k]) }
            }
            expected.isArray -> {
                assertEquals("size at $path", expected.size(), actual.size())
                expected.forEachIndexed { i, v -> assertSameJson("$path/$i", v, actual[i]) }
            }
            expected.isNumber -> assertEquals("number at $path", expected.doubleValue(), actual.doubleValue(), 1e-9)
            else -> assertEquals("value at $path", expected, actual)
        }
    }

    @Test
    fun mainSceneWritesBackEqual() {
        val original = mainSceneEcs()
        val scene = EcsConfigurator(untitledAssets()).load(original)
        val written = SceneEcsWriter().write(scene.engine, scene.document)
        assertSameJson("ecs", original, written)
        assertEquals(listOf("entities", "archetypes", "componentIdentifiers", "metadata"), written.fieldNames().asSequence().toList())
    }

    @Test
    fun mainSceneRoundTrips() {
        val configurator = EcsConfigurator(untitledAssets())
        val first = configurator.load(mainSceneEcs())
        val written = SceneEcsWriter().write(first.engine, first.document)
        val second = configurator.load(written)
        assertSameJson("ecs", written, SceneEcsWriter().write(second.engine, second.document))
        assertEquals(first.engine.entities.size(), second.engine.entities.size())
    }

    @Test
    fun noDerivedStateIsWritten() {
        val scene = EcsConfigurator(untitledAssets()).load(mainSceneEcs())
        val text = SceneEcsWriter().write(scene.engine, scene.document).toString()
        assertFalse(text.contains("combined"))
        assertFalse(text.contains("lightInstance"))
        assertFalse(text.contains("point1"))
    }
}
