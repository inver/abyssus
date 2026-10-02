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

package net.nevinsky.abyssus.sceneview.gizmo

import net.nevinsky.abyssus.dto.SceneReader
import net.nevinsky.abyssus.sceneview.SceneContent
import net.nevinsky.abyssus.sceneview.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GizmoHandlesTest {
    private fun handles(eyeZ: Float) =
        GizmoHandles.of(Vec3(0f, 0f, 0f), GizmoMode.MOVE, Vec3(0f, 0f, eyeZ), 60f, 600)

    @Test
    fun handleLengthDoublesWhenTheEyeDistanceDoubles() {
        assertEquals(handles(10f).size * 2f, handles(20f).size, 1e-4f)
    }

    @Test
    fun handleSpansAConstantNumberOfPixels() {
        val h = handles(10f)
        assertEquals(GizmoHandles.SIZE_PIXELS, h.size / h.worldPerPixel, 1e-3f)
        assertEquals(h.size, h.tip(GizmoAxis.X).x, 1e-5f)
        assertEquals(0f, h.tip(GizmoAxis.X).y, 0f)
    }

    private fun content(text: String) = SceneContent.of(SceneReader.parse(text))

    @Test
    fun lookAtCameraAndPointLightCannotRotate() {
        val main = content(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText())
        assertFalse(canRotate(main, "4"))
        assertTrue(canRotate(main, "0"))
        val lights = content("""{"ecs":{"entities":{
            "1":{"components":{"TypeComponent":{"type":"LIGHT_POINT"},"LightComponent":{"light":{}}}},
            "2":{"components":{"TypeComponent":{"type":"LIGHT_SPOT"},"LightComponent":{"light":{}}}},
            "3":{"components":{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{"light":{}}}},
            "4":{"components":{"CameraComponent":{"camera":{}},"PositionComponent":{"lookAtId":-1}}},
            "5":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"TERRAIN","assetName":"t"}}}}}}}}""")
        assertFalse(canRotate(lights, "1"))
        assertTrue(canRotate(lights, "2"))
        assertTrue(canRotate(lights, "3"))
        assertTrue(canRotate(lights, "4"))
        assertTrue(canRotate(lights, "5"))
    }

    @Test
    fun cameraWhoseTargetIsMissingCanRotate() {
        val c = content("""{"ecs":{"entities":{"4":{"components":{"CameraComponent":{"camera":{}},"PositionComponent":{"lookAtId":99}}}}}}""")
        assertTrue(canRotate(c, "4"))
    }
}
