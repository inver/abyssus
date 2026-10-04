/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.schema

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.testing.warningsTo
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.runtime.SceneLoading
import net.nevinsky.abyssus.runtime.testJson
import net.nevinsky.abyssus.runtime.testProject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The game's [ReflectiveCodec] and the editor's [SchemaJson] must write the same text for every field type. */
class SchemaJsonRoundTripTest {
    private val codec = GameComponents(PlaneRegistry()).codecs.single() as ReflectiveCodec<*>
    private val json = SchemaJson()

    @Suppress("UNCHECKED_CAST")
    private fun write(plane: PlaneComponent) = (codec as ReflectiveCodec<PlaneComponent>).write(plane)

    @Test fun everyFieldTypeGivesIdenticalText() {
        val plane = PlaneComponent().apply {
            lineLength = 25f; fuelSeconds = 90; hasTipWeight = false; name = "Ace"; kind = PlaneComponent.Kind.SPEED
            leadout = Vector3(0.1f, 0f, -0.5f); paint = Color(1f, 0f, 0f, 1f); pilot = 1; model = "tree"
        }
        val reflective = write(plane)
        val values = json.decode(codec.schema, reflective)
        assertEquals(reflective.toString(), json.encode(codec.schema, values).toString())
        val again = codec.read(testJson(reflective.toString())) as PlaneComponent
        assertEquals(reflective, write(again))
        assertEquals(25f, again.lineLength)
        assertEquals(PlaneComponent.Kind.SPEED, again.kind)
        assertEquals(Vector3(0.1f, 0f, -0.5f), again.leadout)
        assertEquals(1, again.pilot)
    }

    @Test fun defaultsGiveTheSameEmptyObjectBothWays() {
        assertEquals("{}", write(PlaneComponent()).toString())
        assertEquals("{}", json.encode(codec.schema, json.decode(codec.schema, testJson("{}"))).toString())
    }

    @Test fun theCustomSceneLoadsItsPlaneAndReportsUnusableValuesOnce() {
        val messages = mutableListOf<String>()
        val loading = SceneLoading(JsonProcessor(), warningsTo(messages), registry = PlaneRegistry())
        val loaded = requireNotNull(loading.load(testProject("Custom").toPath().resolve("scenes/Field.scene")))
        val plane = loaded.engine.ids[0]!!.getComponent(PlaneComponent::class.java)
        assertEquals(22f, plane.lineLength)
        assertEquals(PlaneComponent.Kind.STUNT, plane.kind)
        assertEquals(60, plane.fuelSeconds)
        assertEquals(null, loaded.engine.ids[1]!!.getComponent(PlaneComponent::class.java))
        assertEquals(emptyList<String>(), messages)

        val text = java.nio.file.Files.readString(testProject("Custom").toPath().resolve("scenes/Field.scene"))
            .replace("\"lineLength\": 22", "\"lineLength\": \"long\"")
        val broken = requireNotNull(loading.load(text, testProject("Custom").toPath()))
        assertEquals(18f, broken.engine.ids[0]!!.getComponent(PlaneComponent::class.java).lineLength)
        assertEquals(setOf(0, 1), broken.engine.ids.ids)
        assertEquals(1, messages.size)
        assertEquals("entity 0: PlaneComponent field lineLength: \"long\" is not a decimal number; the default 18 is used", messages.single())
    }
}
