/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.filetype.SceneJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SceneTransformWriterTest {
    private val text = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    private fun components(root: JsonNode, id: String) = root.get("ecs").get("entities").get(id).get("components")

    @Test
    fun droppingAModelChangesOnlyYAndPreservesNumberText() {
        val text = """{"ecs":{"entities":{"0":{"components":{"PositionComponent":{"localPosition":{"x":1.230000,"y":5.000,"z":-2.34000}}}}}}}"""
        val root = SceneJson.parse(text)
        assertTrue(SceneTransformWriter.apply(root, "0", TransformEdit(position = Vec3(1.23f, 1f, -2.34f))))
        assertEquals(text.replace("5.000", "1.0"), SceneJson.compact(root))
    }

    @Test
    fun droppingACameraChangesOnlyBothYsAndPreservesTheRest() {
        val root = SceneJson.parse(text)
        val before = SceneJson.parse(text)
        val c = components(root, "4")
        val local = c["PositionComponent"]["localPosition"] as com.fasterxml.jackson.databind.node.ObjectNode
        val camera = c["CameraComponent"]["camera"]["position"] as com.fasterxml.jackson.databind.node.ObjectNode
        assertEquals(local, camera)
        val p = Vec3(local["x"].floatValue(), local["y"].floatValue() - 5f, local["z"].floatValue())
        assertTrue(SceneTransformWriter.apply(root, "4", TransformEdit(position = p)))
        assertEquals(p.y, local["y"].floatValue(), 0f)
        assertEquals(p.y, camera["y"].floatValue(), 0f)
        // Restoring the two changed values reproduces every key, value and original number text exactly.
        local.set<JsonNode>("y", components(before, "4")["PositionComponent"]["localPosition"]["y"])
        camera.set<JsonNode>("y", components(before, "4")["CameraComponent"]["camera"]["position"]["y"])
        assertEquals(SceneJson.compact(before), SceneJson.compact(root))
    }

    @Test
    fun movingAnEntityChangesOnlyItsLocalPositionX() {
        val root = SceneJson.parse(text)
        val before = components(root, "0").get("PositionComponent").get("localPosition")
        val y = before.get("y").asText()
        val z = before.get("z").asText()
        assertTrue(SceneTransformWriter.apply(root, "0", TransformEdit(position = Vec3(-3.035308f + 2f, 0.9123962f, -3.2570944f))))
        val after = components(root, "0").get("PositionComponent").get("localPosition")
        assertEquals(-1.035308f, after.get("x").floatValue(), 1e-5f)
        assertEquals(y, after.get("y").asText())
        assertEquals(z, after.get("z").asText())
        // every other entity and value is as it was
        val untouched = SceneJson.parse(text)
        (components(untouched, "0").get("PositionComponent").get("localPosition") as com.fasterxml.jackson.databind.node.ObjectNode).set<JsonNode>("x", after.get("x"))
        assertEquals(untouched, root)
    }

    @Test
    fun movingACameraChangesBothPositions() {
        val root = SceneJson.parse(text)
        val p = components(root, "4").get("PositionComponent").get("localPosition")
        val moved = Vec3(p.get("x").floatValue(), p.get("y").floatValue() + 1f, p.get("z").floatValue())
        assertTrue(SceneTransformWriter.apply(root, "4", TransformEdit(position = moved)))
        val c = components(root, "4")
        assertEquals(moved.y, c.get("PositionComponent").get("localPosition").get("y").floatValue(), 1e-5f)
        assertEquals(moved.y, c.get("CameraComponent").get("camera").get("position").get("y").floatValue(), 1e-5f)
        assertEquals(12.318308f + 1f, moved.y, 1e-5f)
    }

    @Test
    fun rotatingACameraWritesItsViewPoint() {
        val root = SceneJson.parse(text)
        val dir = Vec3(1f, 0f, 0f)
        assertTrue(SceneTransformWriter.apply(root, "4", TransformEdit(rotation = Quat(0f, 0.7071f, 0f, 0.7071f), direction = dir)))
        val c = components(root, "4")
        assertEquals(1f, c.get("CameraComponent").get("camera").get("viewPointPosition").get("x").floatValue(), 0f)
        assertEquals(0.7071f, c.get("PositionComponent").get("localRotation").get("y").floatValue(), 0f)
    }

    @Test
    fun rotatingAnEntityWithoutLocalRotationAddsAllFourFields() {
        val root = SceneJson.parse(text)
        assertNull(components(root, "0").get("PositionComponent").get("localRotation"))
        assertTrue(SceneTransformWriter.apply(root, "0", TransformEdit(rotation = Quat(0f, 0.7071f, 0f, 0.7071f))))
        val q = components(root, "0").get("PositionComponent").get("localRotation")
        assertEquals(listOf("x", "y", "z", "w"), q.fieldNames().asSequence().toList())
        assertEquals(0.7071f, q.get("w").floatValue(), 0f)
    }

    @Test
    fun anEntityWithoutAPositionComponentGetsOne() {
        val root = SceneJson.parse("""{"ecs":{"entities":{"1":{"components":{"NameComponent":{"name":"a"}}}}}}""")
        assertTrue(SceneTransformWriter.apply(root, "1", TransformEdit(position = Vec3(1f, 0f, 0f))))
        assertEquals(1f, components(root, "1").get("PositionComponent").get("localPosition").get("x").floatValue(), 0f)
    }

    @Test
    fun anUnknownEntityReturnsFalse() {
        val root = SceneJson.parse(text)
        assertFalse(SceneTransformWriter.apply(root, "99", TransformEdit(position = Vec3(1f, 2f, 3f))))
        assertEquals(SceneJson.parse(text), root)
    }

    @Test
    fun anEditEqualToTheCurrentValuesReturnsFalse() {
        val root = SceneJson.parse(text)
        val p = components(root, "0").get("PositionComponent").get("localPosition")
        val same = Vec3(p.get("x").floatValue(), p.get("y").floatValue(), p.get("z").floatValue())
        assertFalse(SceneTransformWriter.apply(root, "0", TransformEdit(position = same)))
        assertFalse(SceneTransformWriter.apply(root, "0", TransformEdit(rotation = Quat.IDENTITY)))
        assertEquals(SceneJson.parse(text), root)
    }

    @Test
    fun aHandleMoveWritesTheHandlePosition() {
        val mundus = File("src/test/testData/project/Lights/scenes/Mundus Lights.scene").readText()
        val root = SceneJson.parse(mundus)
        val handle = components(root, "0").get("PositionComponent")
        // The fixture's handle has an empty PositionComponent and no localPosition.
        assertFalse(handle.has("localPosition"))
        assertTrue(SceneTransformWriter.apply(root, "1", TransformEdit(target = TargetMove("0", Vec3(0f, 10f, -10f)))))
        val written = components(root, "0").get("PositionComponent")
        assertEquals(0f, written.get("localPosition").get("x").floatValue(), 0f)
        assertEquals(10f, written.get("localPosition").get("y").floatValue(), 0f)
        assertEquals(-10f, written.get("localPosition").get("z").floatValue(), 0f)
        // The light's own PositionComponent is unchanged: only lookAtId and localPosition.y.
        val light = components(root, "1").get("PositionComponent")
        assertEquals(0, light.get("lookAtId").intValue())
        assertEquals(10f, light.get("localPosition").get("y").floatValue(), 0f)
        assertEquals(1, light.get("localPosition").size())
        assertFalse(light.has("localRotation"))
        // No key is added to any LightComponent.
        val lightComponent = components(root, "1").get("LightComponent")
        assertEquals(0, lightComponent.size())
        // Every other entity is untouched.
        val untouched = SceneJson.parse(mundus)
        (components(untouched, "0").get("PositionComponent") as com.fasterxml.jackson.databind.node.ObjectNode)
            .set<JsonNode>("localPosition", written.get("localPosition"))
        assertEquals(SceneJson.compact(untouched), SceneJson.compact(root))
    }

    @Test
    fun aHandleMoveToTheSamePlaceChangesNothing() {
        val mundus = File("src/test/testData/project/Lights/scenes/Mundus Lights.scene").readText()
        val root = SceneJson.parse(mundus)
        val same = Vec3(0f, 10f, -10f)
        assertTrue(SceneTransformWriter.apply(root, "1", TransformEdit(target = TargetMove("0", same))))
        // Writing the same value again changes nothing.
        assertFalse(SceneTransformWriter.apply(root, "1", TransformEdit(target = TargetMove("0", same))))
        // The final state is the fixture with the handle's localPosition set and nothing else changed.
        val expected = SceneJson.parse(mundus)
        (components(expected, "0").get("PositionComponent") as com.fasterxml.jackson.databind.node.ObjectNode)
            .set<JsonNode>("localPosition", components(root, "0").get("PositionComponent").get("localPosition"))
        assertEquals(SceneJson.compact(expected), SceneJson.compact(root))
    }
}
