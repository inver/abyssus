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
}
