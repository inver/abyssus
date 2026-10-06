/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.runtime.schema

import net.nevinsky.abyssus.lib.runtime.testJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaJsonTest {
    private val schema = ComponentSchemaReader().read(PlaneComponent::class.java)
    private val json = SchemaJson()
    private val defaults get() = schema.fields.associate { it.name to it.default }

    private fun decode(text: String, problems: MutableList<String> = mutableListOf()) = json.decode(schema, testJson(text)) { problems += it }

    @Test fun planeValuesLoadWithEveryOtherFieldAtItsDefault() {
        val values = decode("""{"lineLength": 22, "kind": "STUNT"}""")
        assertEquals(defaults + mapOf("lineLength" to 22f, "kind" to "STUNT"), values)
    }

    @Test fun anEmptyComponentHoldsTheDefaults() {
        assertEquals(defaults, decode("{}"))
    }

    @Test fun everyTypeRoundTrips() {
        val changed = mapOf(
            "lineLength" to 21.5f, "fuelSeconds" to 90, "hasTipWeight" to false, "name" to "Ace", "kind" to "SPEED",
            "leadout" to SchemaVector(0.1f, 0f, -0.5f), "paint" to SchemaColor(1f, 0f, 0f, 1f), "pilot" to 1, "model" to "tree",
        )
        val written = json.encode(schema, changed)
        assertEquals(
            """{"lineLength":21.5,"fuelSeconds":90,"hasTipWeight":false,"name":"Ace","kind":"SPEED","leadout":{"x":0.1,"y":0,"z":-0.5},"paint":{"r":1,"g":0,"b":0,"a":1},"pilot":1,"model":"tree"}""",
            written.toString(),
        )
        assertEquals(changed, json.decode(schema, testJson(written.toString())))
    }

    @Test fun aWholeDecimalIsWrittenWithoutAFraction() {
        val values = decode("""{"lineLength": 25.0}""")
        assertEquals("""{"lineLength":25}""", json.encode(schema, values).toString())
    }

    @Test fun defaultsAreNotWritten() {
        assertEquals("""{"kind":"STUNT"}""", json.encode(schema, defaults + mapOf("lineLength" to 18f, "kind" to "STUNT")).toString())
        assertEquals("{}", json.encode(schema, defaults).toString())
    }

    @Test fun aVectorIsWrittenWholeWhenAnyPartDiffers() {
        assertEquals("""{"leadout":{"x":0,"y":0.5,"z":-0.3}}""", json.encode(schema, defaults + ("leadout" to SchemaVector(0f, 0.5f, -0.3f))).toString())
        assertEquals(SchemaVector(0f, 0.5f, -0.3f), decode("""{"leadout": {"y": 0.5}}""")["leadout"])
    }

    @Test fun textWhereANumberBelongsFallsBackWithOneMessage() {
        val problems = mutableListOf<String>()
        assertEquals(18f, decode("""{"lineLength": "long"}""", problems)["lineLength"])
        assertEquals(1, problems.size)
        assertTrue(problems.single(), problems.single().contains("lineLength"))
    }

    @Test fun belowTheMinimumFallsBackNamingTheLimit() {
        val problems = mutableListOf<String>()
        assertEquals(18f, decode("""{"lineLength": 2}""", problems)["lineLength"])
        assertEquals(listOf("field lineLength: 2 is below the minimum 5; the default 18 is used"), problems)
    }

    @Test fun otherUnusableValuesFallBack() {
        val problems = mutableListOf<String>()
        val values = decode("""{"kind": "JET", "lineLength": 31, "hasTipWeight": 1, "pilot": "two", "leadout": [1]}""", problems)
        assertEquals("TRAINER", values["kind"])
        assertEquals(18f, values["lineLength"])
        assertEquals(true, values["hasTipWeight"])
        assertEquals(-1, values["pilot"])
        assertEquals(SchemaVector(0f, 0f, -0.3f), values["leadout"])
        assertEquals(5, problems.size)
    }

    @Test fun everyTypeRejectsAnArrayAndKeepsItsDefault() {
        assertEquals(FieldType.entries.toSet(), schema.fields.map { it.type }.toSet())
        for (field in schema.fields) {
            val problems = mutableListOf<String>()
            val values = decode("""{"${field.name}": []}""", problems)
            assertEquals(field.name, field.default, values[field.name])
            assertEquals(field.name, 1, problems.size)
            assertTrue(problems.single(), problems.single().contains("field ${field.name}:"))
            assertEquals(field.name, "{}", json.encode(schema, values).toString())
        }
    }

    @Test fun wholeValuesOutsideTheirLimitKeepTheDefault() {
        val problems = mutableListOf<String>()
        assertEquals(60, decode("""{"fuelSeconds": -1}""", problems)["fuelSeconds"])
        assertEquals(listOf("field fuelSeconds: -1 is below the minimum 0; the default 60 is used"), problems)
    }

    @Test fun aVectorAxisOutsideItsLimitFallsBackNamingTheAxis() {
        val box = ComponentSchemaReader().read(BoxComponent::class.java)
        assertEquals(0.0, box.fields.single().min)
        val problems = mutableListOf<String>()
        val values = json.decode(box, testJson("""{"halfExtents": {"x": 1, "y": 0, "z": 1}}""")) { problems += it }
        assertEquals(SchemaVector(0.5f, 0.5f, 0.5f), values["halfExtents"])
        assertEquals(listOf("field halfExtents: y: 0 is not greater than the minimum 0; the default {\"x\":0.5,\"y\":0.5,\"z\":0.5} is used"), problems)
        assertEquals(SchemaVector(1f, 0.5f, 0.5f), json.decode(box, testJson("""{"halfExtents": {"x": 1}}"""))["halfExtents"])
    }
}

/** A vector field whose every axis must be greater than `0`. */
@SceneComponent("BoxComponent")
class BoxComponent : com.badlogic.ashley.core.Component {
    @Field(min = 0.0, minExclusive = true)
    var halfExtents = com.badlogic.gdx.math.Vector3(0.5f, 0.5f, 0.5f)
}
