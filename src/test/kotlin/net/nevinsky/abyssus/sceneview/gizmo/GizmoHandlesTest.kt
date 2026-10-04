/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview.gizmo

import net.nevinsky.abyssus.parseScene
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

    private fun content(text: String) = SceneContent.of(parseScene(text))

    @Test
    fun lookAtCameraAndPointLightCannotRotate() {
        val main = content(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText())
        assertFalse(canRotate(main, "4"))
        assertTrue(canRotate(main, "0"))
        val lights = content("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{
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
        val c = content("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"4":{"components":{"CameraComponent":{"camera":{}},"PositionComponent":{"lookAtId":99}}}}}}""")
        assertTrue(canRotate(c, "4"))
    }

    @Test
    fun handleLightsCanRotateAndALightAimedAtAModelCannot() {
        val mundus = content(File("src/test/testData/project/Lights/scenes/Abyssus Lights.scene").readText())
        // A directional or spot light that looks at a direction handle keeps its rings.
        assertTrue(canRotate(mundus, "1"))
        assertTrue(canRotate(mundus, "4"))
        // A light aimed at something other than a handle (a model) only moves.
        val aimedAtModel = content("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{
            "m":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"a"}}},
                "PositionComponent":{"localPosition":{"x":1}}}},
            "l":{"components":{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{},
                "PositionComponent":{"lookAtId":"m","localPosition":{"x":5}}}}}}}""")
        assertFalse(canRotate(aimedAtModel, "l"))
        // A light without a look-at target keeps its rings; a point light never has any.
        val noTarget = content("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{
            "d":{"components":{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{}}}}}}""")
        assertTrue(canRotate(noTarget, "d"))
        val point = content("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{
            "p":{"components":{"TypeComponent":{"type":"LIGHT_POINT"},"LightComponent":{}}}}}}""")
        assertFalse(canRotate(point, "p"))
    }
}
