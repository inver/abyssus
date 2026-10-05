/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.core.JsonProcessor
import java.io.File
import net.nevinsky.abyssus.runtime.schema.GameComponents
import net.nevinsky.abyssus.runtime.schema.PlaneComponent
import net.nevinsky.abyssus.runtime.schema.PlaneRegistry
import net.nevinsky.abyssus.runtime.testConfigurator
import net.nevinsky.abyssus.runtime.testJson
import net.nevinsky.abyssus.runtime.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EcsWriterTest {
    private fun writer(game: GameComponents = GameComponents()) = EcsWriter(JsonProcessor().mapper, game)

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

    /** [ecs] without the Mundus-era `archetype` of each entity and the `archetypes` table, which are not carried. */
    private fun withoutArchetypes(ecs: JsonNode): JsonNode = ecs.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().also { copy ->
        copy.remove("archetypes")
        (copy["entities"] ?: copy).forEach { (it as com.fasterxml.jackson.databind.node.ObjectNode).remove("archetype") }
    }

    @Test
    fun mainSceneWritesBackEqual() {
        val original = mainSceneEcs()
        val scene = testConfigurator(untitledAssets()).load(original)
        val written = writer().write(scene.engine, scene.document)
        assertSameJson("ecs", withoutArchetypes(original), written)
        assertEquals("the block is the entity map, in ascending id order", (0..8).map { it.toString() }, written.fieldNames().asSequence().toList())
    }

    @Test
    fun mainSceneRoundTrips() {
        val configurator = testConfigurator(untitledAssets())
        val first = configurator.load(mainSceneEcs())
        val written = writer().write(first.engine, first.document)
        val second = configurator.load(written)
        assertSameJson("ecs", written, writer().write(second.engine, second.document))
        assertEquals(first.engine.entities.size(), second.engine.entities.size())
    }

    private fun customEcs() = testJson(File(testProject("Custom"), "scenes/Field.scene").readText())["ecs"]

    @Test
    fun declaredComponentWritesNoIdentifierTable() {
        val game = GameComponents(PlaneRegistry())
        val scene = testConfigurator(game = game).load(customEcs())
        scene.engine.ids[1]!!.add(PlaneComponent())
        val written = writer(game).write(scene.engine, scene.document)
        assertEquals("{}", written["entities"]["1"]["components"]["PlaneComponent"].toString())
        assertEquals("""{"lineLength":22,"kind":"STUNT"}""", written["entities"]["0"]["components"]["PlaneComponent"].toString())
        assertEquals(listOf("entities"), written.fieldNames().asSequence().toList())
        assertFalse(written.toString().contains("componentIdentifiers"))
        assertFalse(written.toString().contains(PlaneComponent::class.java.name))
    }

    @Test
    fun unregisteredGameComponentIsWrittenBackAsItWas() {
        val ecs = customEcs()
        val scene = testConfigurator().load(ecs)
        val written = writer().write(scene.engine, scene.document)
        assertEquals(ecs["entities"]["0"]["components"]["PlaneComponent"], written["entities"]["0"]["components"]["PlaneComponent"])
    }

    @Test
    fun noDerivedStateIsWritten() {
        val scene = testConfigurator(untitledAssets()).load(mainSceneEcs())
        val text = writer().write(scene.engine, scene.document).toString()
        assertFalse(text.contains("combined"))
        assertFalse(text.contains("lightInstance"))
        assertFalse(text.contains("point1"))
    }

    // --- Jackson writing ---

    private val packageName = "net.nevinsky.abyssus.runtime.ecs.component"

    private fun roundTrip(ecs: String, game: GameComponents = GameComponents()): JsonNode {
        val scene = testConfigurator(untitledAssets(), game = game).load(testJson(ecs))
        return writer(game).write(scene.engine, scene.document)
    }

    @Test
    fun componentsAreWrittenInAFixedOrderUnderShortNamesWithWhatWasCarriedAfter() {
        val ecs = """{"0":{"components":{
            "Zeta":{"kept":true},
            "$packageName.ParentComponent":{"parentEntityId":0},
            "TypeComponent":{"type":"OBJECT"},
            "$packageName.NameComponent":{"name":"A"},
            "no.such.Class":{}}}}"""
        val components = roundTrip(ecs)["0"]["components"]
        assertEquals(
            listOf("NameComponent", "TypeComponent", "ParentComponent", "Zeta", "no.such.Class"),
            components.fieldNames().asSequence().toList(),
        )
        assertEquals("""{"name":"A"}""", components["NameComponent"].toString())
        assertEquals("""{"kept":true}""", components["Zeta"].toString())
    }

    @Test
    fun entitiesAreWrittenInAscendingOrderOfTheirIdComponentNotTheOrderTheyWereAdded() {
        val scene = testConfigurator().load(testJson("""{"10":{"components":{}},"2":{"components":{}},"1":{"components":{}}}"""))
        val extra = com.badlogic.ashley.core.Entity().also {
            it.add(net.nevinsky.abyssus.runtime.ecs.component.NameComponent("late"))
            scene.engine.addEntity(it)
        }
        val added = com.badlogic.ashley.core.Entity().also {
            it.add(net.nevinsky.abyssus.runtime.ecs.component.IdComponent(5L))
            scene.engine.addEntity(it)
        }
        val written = writer().write(scene.engine, scene.document)
        assertEquals(listOf("1", "2", "5", "10", "11"), written.fieldNames().asSequence().toList())
        assertEquals("""{"name":"late"}""", written["11"]["components"]["NameComponent"].toString())
        assertEquals(extra, scene.engine.entities.last { it.getComponent(net.nevinsky.abyssus.runtime.ecs.component.IdComponent::class.java) == null })
        assertEquals(added, scene.engine.entities.first { it.getComponent(net.nevinsky.abyssus.runtime.ecs.component.IdComponent::class.java)?.id == 5L })
    }

    @Test
    fun aSceneKeepsTheShapeItWasLoadedIn() {
        val wrapped = roundTrip("""{"entities":{"0":{"components":{"NameComponent":{"name":"A"}}}},"metadata":{"v":1}}""")
        assertEquals(listOf("entities", "metadata"), wrapped.fieldNames().asSequence().toList())
        val direct = roundTrip("""{"0":{"components":{"NameComponent":{"name":"A"}}}}""")
        assertEquals(listOf("0"), direct.fieldNames().asSequence().toList())
    }

    @Test
    fun aComponentTheFileDidNotHaveIsAddedUnderItsShortNameAndARemovedOneIsDropped() {
        val scene = testConfigurator().load(testJson("""{"entities":{"0":{"components":{"NameComponent":{"name":"A"},"TypeComponent":{"type":"OBJECT"}}}}}"""))
        val entity = scene.engine.ids[0]!!
        entity.remove(net.nevinsky.abyssus.runtime.ecs.component.TypeComponent::class.java)
        entity.add(net.nevinsky.abyssus.runtime.ecs.component.ParentComponent(0))
        val written = writer().write(scene.engine, scene.document)["entities"]["0"]["components"]
        assertEquals(listOf("NameComponent", "ParentComponent"), written.fieldNames().asSequence().toList())
        assertEquals("""{"parentEntityId":0}""", written["ParentComponent"].toString())
    }

    @Test
    fun anEmptyOrDefaultComponentIsWrittenAsAnEmptyObject() {
        val components = roundTrip(
            """{"entities":{"0":{"components":{"NameComponent":{},"ParentComponent":{"parentEntityId":-1},"PositionComponent":{}}}}}""",
        )["entities"]["0"]["components"]
        assertEquals("{}", components["NameComponent"].toString())
        assertEquals("{}", components["ParentComponent"].toString())
        assertEquals("{}", components["PositionComponent"].toString())
    }

    @Test
    fun aPositionWritesOnlyTheAxesThatDifferFromTheDefaults() {
        val position = roundTrip(
            """{"entities":{"0":{"components":{"PositionComponent":{
                "localPosition":{"x":1.5,"y":0,"z":0},"localScale":{"x":2,"y":1,"z":1},"localRotation":{"w":1,"y":0.5}}}}}}""",
        )["entities"]["0"]["components"]["PositionComponent"]
        assertEquals("""{"localRotation":{"y":0.5},"localPosition":{"x":1.5},"localScale":{"x":2}}""", position.toString())
    }

    @Test
    fun anUnchangedLightWritesTheFilesOwnNodeIncludingWhatItDoesNotModel() {
        val light = """{"outerUnknown":7.000,"light":{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":2.50,"future":1.23400}}"""
        val written = roundTrip("""{"entities":{"0":{"components":{"LightComponent":$light}}}}""")["entities"]["0"]["components"]["LightComponent"]
        assertEquals(light, written.toString())
    }

    @Test
    fun aGameComponentWritesOnlyItsFieldsUnderItsShortName() {
        val game = GameComponents(PlaneRegistry())
        val fullName = PlaneComponent::class.java.name
        val written = roundTrip(
            """{"0":{"components":{"$fullName":{"lineLength":22,"kind":"STUNT","speed":5}}},"1":{"components":{"PlaneComponent":{}}}}""",
            game,
        )
        assertEquals("""{"lineLength":22,"kind":"STUNT"}""", written["0"]["components"]["PlaneComponent"].toString())
        assertEquals("{}", written["1"]["components"]["PlaneComponent"].toString())
    }

    @Test
    fun aRawComponentIsWrittenUnchangedAndKeepsItsOrder() {
        val ecs = """{"entities":{"0":{"components":{"Mystery":{"a":[1,2]},"NameComponent":{"name":"A"},"no.such.Class":{}}}},"metadata":{"v":1}}"""
        val written = roundTrip(ecs)
        assertEquals(testJson(ecs), written)
        assertEquals(listOf("entities", "metadata"), written.fieldNames().asSequence().toList())
    }

    @Test
    fun theMundusArchetypeOfAnEntityAndTheArchetypesTableAreNotCarried() {
        val written = roundTrip(
            """{"entities":{"0":{"archetype":3,"components":{"NameComponent":{"name":"A"}}}},"archetypes":{"3":["NameComponent"]},"metadata":{"v":1}}""",
        )
        assertEquals("""{"entities":{"0":{"components":{"NameComponent":{"name":"A"}}}},"metadata":{"v":1}}""", written.toString())
    }
}
