/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.schema

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.testing.warningsTo
import net.nevinsky.abyssus.core.io.FileLoader
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.runtime.RuntimeSceneLoader
import net.nevinsky.abyssus.runtime.loadComponent
import net.nevinsky.abyssus.runtime.testJson
import net.nevinsky.abyssus.runtime.writeComponent
import net.nevinsky.abyssus.runtime.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The writer's Jackson output for a game component and the editor's [SchemaJson] must give the same text for every field type. */
class SchemaJsonRoundTripTest {
    private val game = GameComponents(PlaneRegistry())
    private val schema = game.schemas.single()
    private val json = SchemaJson()

    private fun write(plane: PlaneComponent) = writeComponent(plane, game)

    @Test fun everyFieldTypeGivesIdenticalText() {
        val plane = PlaneComponent().apply {
            lineLength = 25f; fuelSeconds = 90; hasTipWeight = false; name = "Ace"; kind = PlaneComponent.Kind.SPEED
            leadout = Vector3(0.1f, 0f, -0.5f); paint = Color(1f, 0f, 0f, 1f); pilot = 1; model = "tree"
        }
        val reflective = write(plane)
        val values = json.decode(schema, reflective)
        assertEquals(reflective.toString(), json.encode(schema, values).toString())
        val again = loadComponent(PlaneComponent::class.java, testJson(reflective.toString()), game = GameComponents(PlaneRegistry()))
        assertEquals(reflective, write(again))
        assertEquals(25f, again.lineLength)
        assertEquals(PlaneComponent.Kind.SPEED, again.kind)
        assertEquals(Vector3(0.1f, 0f, -0.5f), again.leadout)
        assertEquals(1, again.pilot)
    }

    @Test fun defaultsGiveTheSameEmptyObjectBothWays() {
        assertEquals("{}", write(PlaneComponent()).toString())
        assertEquals("{}", json.encode(schema, json.decode(schema, testJson("{}"))).toString())
    }

    @Test fun aGameValueJacksonCannotBindKeepsThePlaneRawWithOneWarning() {
        val messages = mutableListOf<String>()
        val loader = RuntimeSceneLoader(JsonProcessor(), FileLoader(testProject("Custom")), warningsTo(messages), PlaneRegistry())
        val text = java.nio.file.Files.readString(testProject("Custom").toPath().resolve("scenes/Field.scene"))
            .replace("\"lineLength\": 22", "\"lineLength\": \"long\"")
        val broken = requireNotNull(loader.loadFromText(text))
        assertNull("a value Jackson cannot bind keeps the component raw", broken.engine.ids[0]!!.getComponent(PlaneComponent::class.java))
        assertEquals(setOf(0, 1), broken.engine.ids.ids)
        assertEquals(1, messages.size)
        assertTrue(messages.single(), messages.single().startsWith("entity 0: component PlaneComponent could not be read"))
    }
}
