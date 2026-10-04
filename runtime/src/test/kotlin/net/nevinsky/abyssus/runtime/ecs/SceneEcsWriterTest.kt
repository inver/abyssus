/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsWriter
import java.io.File
import net.nevinsky.abyssus.runtime.ecs.scene.ComponentCodecs
import net.nevinsky.abyssus.runtime.schema.GameComponents
import net.nevinsky.abyssus.runtime.schema.PlaneComponent
import net.nevinsky.abyssus.runtime.schema.PlaneRegistry
import net.nevinsky.abyssus.runtime.testJson
import net.nevinsky.abyssus.runtime.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SceneEcsWriterTest {
    @Test fun rawLegacyExtrasAreRefusedByWriter() {
        val document = net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsDocument(linkedMapOf("componentIdentifiers" to net.nevinsky.abyssus.runtime.testJson("{}")), emptyList())
        org.junit.Assert.assertThrows(net.nevinsky.abyssus.assets.format.UnsupportedDocumentFormat::class.java) {
            SceneEcsWriter().write(net.nevinsky.abyssus.runtime.ecs.scene.SceneEngine(), document)
        }
    }

    /** Numbers by value, objects by key regardless of order, so `100` and `100.0` or key order do not matter. */
    private fun assertSameJson(path: String, expected: JsonNode, actual: JsonNode) {
        when {
            expected.isObject -> {
                // the writer leaves out a zero number (a default), which a document may spell out explicitly
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
        assertEquals(listOf("entities", "archetypes", "metadata"), written.fieldNames().asSequence().toList())
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

    private fun customEcs() = testJson(File(testProject("Custom"), "scenes/Field.scene").readText())["ecs"]

    @Test
    fun declaredComponentWritesNoIdentifierTable() {
        val game = GameComponents(PlaneRegistry())
        val scene = EcsConfigurator(game = game).load(customEcs())
        scene.engine.ids[1]!!.add(PlaneComponent())
        val written = SceneEcsWriter(ComponentCodecs(game = game)).write(scene.engine, scene.document)
        assertEquals("{}", written["entities"]["1"]["components"]["PlaneComponent"].toString())
        assertEquals("""{"lineLength":22,"kind":"STUNT"}""", written["entities"]["0"]["components"]["PlaneComponent"].toString())
        assertEquals(listOf("entities"), written.fieldNames().asSequence().toList())
        assertFalse(written.toString().contains("componentIdentifiers"))
        assertFalse(written.toString().contains(PlaneComponent::class.java.name))
    }

    @Test
    fun unregisteredGameComponentIsWrittenBackAsItWas() {
        val ecs = customEcs()
        val scene = EcsConfigurator().load(ecs)
        val written = SceneEcsWriter().write(scene.engine, scene.document)
        assertEquals(ecs["entities"]["0"]["components"]["PlaneComponent"], written["entities"]["0"]["components"]["PlaneComponent"])
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
