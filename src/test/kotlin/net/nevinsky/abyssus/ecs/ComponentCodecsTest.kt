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
}
