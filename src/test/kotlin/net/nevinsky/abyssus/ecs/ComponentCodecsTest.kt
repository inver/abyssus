/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.ecs

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.ecs.scene.CameraCodec
import net.nevinsky.abyssus.ecs.scene.LightCodec
import net.nevinsky.abyssus.ecs.scene.PositionCodec
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.scene.ColorDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentCodecsTest {
    private fun json(text: String) = SceneJson.parse(text)

    @Test
    fun cameraRoundTrip() {
        val node = json(
            """{"camera":{"viewPointPosition":{"x":0.5,"y":-0.25,"z":-0.75},"position":{"x":1,"y":2.5,"z":3},
               "far":200,"near":0.5,"fieldOfView":60}}""",
        )
        val camera = CameraCodec().read(node)
        assertEquals(Vector3(0.5f, -0.25f, -0.75f), camera.camera.direction)
        assertEquals(Vector3(1f, 2.5f, 3f), camera.camera.position)
        assertEquals(200f, camera.camera.far, 0f)
        assertEquals(0.5f, camera.camera.near, 0f)
        assertEquals(60f, camera.camera.fieldOfView, 0f)
        val written = CameraCodec().write(camera)
        assertEquals(node, written)
        val again = CameraCodec().read(written)
        assertEquals(camera.camera.direction, again.camera.direction)
        assertEquals(camera.camera.position, again.camera.position)
        assertEquals(60f, again.camera.fieldOfView, 0f)
        assertFalse(written.toString().contains("combined"))
    }

    @Test
    fun lightInBothShapes() {
        val nested = LightCodec().read(json("""{"light":{"color":{"r":1,"g":0.5,"b":0.25,"a":1},"intensity":0.8}}"""))
        val direct = LightCodec().read(json("""{"color":{"r":1,"g":0.5,"b":0.25,"a":1},"intensity":0.8}"""))
        for (light in listOf(nested, direct)) {
            assertEquals(ColorDto(1f, 0.5f, 0.25f, 1f), light.light.color)
            assertEquals(0.8f, light.light.intensity, 0f)
        }
        assertTrue(nested.nested)
        assertFalse(direct.nested)
        assertTrue(LightCodec().write(nested).has("light"))
        val written = LightCodec().write(direct)
        assertFalse(written.has("light"))
        assertEquals(0.8f, written["intensity"].floatValue(), 0f)
        assertEquals(direct.light, LightCodec().read(written).light)
    }

    @Test
    fun rangeRoundTripsInBothShapesAndDefaultIsOmitted() {
        for (text in listOf(
            """{"light":{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":1,"range":30}}""",
            """{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":1,"range":30}""",
        )) {
            val node = json(text)
            val codec = LightCodec()
            val light = codec.read(node)
            assertEquals(30f, light.light.range, 0f)
            assertEquals(text, codec.write(light).toString())
            light.light.range = 100f
            val written = codec.write(light)
            val values = written.get("light") ?: written
            assertFalse(values.has("range"))
            assertEquals(100f, codec.read(written).light.range, 0f)
            assertEquals(written.toString(), codec.write(codec.read(written)).toString())
        }
    }

    @Test
    fun positionDefaultsAndRoundTrip() {
        val empty = PositionCodec().read(json("{}"))
        assertEquals(Vector3(), empty.localPosition)
        assertEquals(1f, empty.localRotation.w, 0f)
        assertEquals(Vector3(1f, 1f, 1f), empty.localScale)
        assertEquals("{}", PositionCodec().write(empty).toString())

        val node = json("""{"localRotation":{"w":0.5,"x":0.5,"y":0.5,"z":0.5},"lookAtId":3,"localPosition":{"x":-3.5,"z":2}}""")
        val position = PositionCodec().read(node)
        assertEquals(Vector3(-3.5f, 0f, 2f), position.localPosition)
        assertEquals(3, position.lookAtId)
        assertEquals(node, PositionCodec().write(position))
    }

    @Test
    fun beamDefaultsAndSavedValuesRoundTripWithoutLosingUnknownData() {
        val codec = LightCodec()
        val defaults = codec.read(json("{}"))
        assertEquals(45f, defaults.light.coneAngle, 0f)
        assertEquals(0.2f, defaults.light.edgeSoftness, 0f)
        val defaultValues = codec.write(defaults)["light"]
        assertFalse(defaultValues.has("coneAngle"))
        assertFalse(defaultValues.has("edgeSoftness"))
        for (nested in listOf(true, false)) {
            val values = """{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":1,"coneAngle":60,"edgeSoftness":0.2500,"future":1.23400}"""
            val text = if (nested) """{"outerUnknown":7.000,"light":$values}""" else values
            val light = codec.read(json(text))
            assertEquals(nested, light.nested)
            assertEquals(60f, light.light.coneAngle, 0f)
            assertEquals(0.25f, light.light.edgeSoftness, 0f)
            assertEquals(text, codec.write(light).toString())
            light.light.coneAngle = 45f
            light.light.edgeSoftness = 0.2f
            val written = codec.write(light)
            val saved = written["light"] ?: written
            assertFalse(saved.has("coneAngle"))
            assertFalse(saved.has("edgeSoftness"))
            assertEquals("1.23400", saved["future"].toString())
            if (nested) assertEquals("7.000", written["outerUnknown"].toString())
        }
    }
}
