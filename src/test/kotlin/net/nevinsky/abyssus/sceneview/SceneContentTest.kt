/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.parseScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SceneContentTest {
    private fun content(json: String) = SceneContent.of(parseScene(json))

    private fun entity(components: String) = """{"ecs":{"entities":{"7":{"archetype":1,"components":$components}}}}"""

    @Test
    fun mainSceneHasThreeModelsAndOneTerrain() {
        val text = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()
        val c = content(text)
        assertEquals(
            listOf("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", "model_fc33e1f1-015b-4524-9b10-aa417acd273c", "model_900f6f61-6384-434a-be81-56ce303fbb56"),
            c.models.map { it.assetName },
        )
        assertEquals(listOf("0", "2", "6"), c.models.map { it.entityId })
        assertEquals(listOf("terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"), c.terrains.map { it.assetName })
        assertTrue(c.lights.isEmpty())
        assertEquals("skybox_physical", c.skybox)
    }

    @Test
    fun transformFieldsDefaultToIdentity() {
        val c = content(entity("""{"PositionComponent":{"localRotation":{"w":0.5,"x":-0.5},"localPosition":{"y":2}},
            "RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"m"}}}}"""))
        val t = c.models.single().transform
        assertEquals(Vec3(0f, 2f, 0f), t.position)
        assertEquals(Quat(-0.5f, 0f, 0f, 0.5f), t.rotation)
        assertEquals(Vec3(1f, 1f, 1f), t.scale)
    }

    @Test
    fun missingPositionComponentIsIdentity() {
        val c = content(entity("""{"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"m"}}}}"""))
        assertEquals(PlacementTransform.IDENTITY, c.models.single().transform)
    }

    @Test
    fun unknownAndMalformedEntitiesAreIgnored() {
        val json = """{"ecs":{"entities":{
            "1":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"SPRITE","assetName":"s"}}}}},
            "2":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"MODEL"}}}}},
            "3":{"components":{"RenderComponent":"nope"}},
            "4":"nope",
            "5":{"components":{"PositionComponent":{"localPosition":{"x":"a"}},"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"ok"}}}}}}}}"""
        val c = content(json)
        assertEquals(listOf("ok"), c.models.map { it.assetName })
        assertEquals(0f, c.models.single().transform.position.x, 0f)
    }

    @Test
    fun skyboxNeedsEnabledAndName() {
        assertEquals("sky", content("""{"skyboxEnabled":true,"skyboxName":"sky"}""").skybox)
        assertNull(content("""{"skyboxEnabled":false,"skyboxName":"sky"}""").skybox)
        assertNull(content("""{"skyboxEnabled":true,"skyboxName":null}""").skybox)
        assertNull(content("""{"skyboxName":"sky"}""").skybox)
    }

    @Test
    fun lightEntitiesAreRead() {
        val c = content(entity("""{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},
            "LightComponent":{"light":{"color":{"r":1.0,"g":0.5,"b":0.0,"a":1.0},"intensity":0.8}},
            "PositionComponent":{"localPosition":{"x":1,"y":10,"z":2}}}"""))
        val l = c.lights.single()
        assertEquals(LightKind.DIRECTIONAL, l.kind)
        assertEquals(Rgba(1f, 0.5f, 0f, 1f), l.color)
        assertEquals(0.8f, l.intensity, 0f)
        assertEquals(Vec3(1f, 10f, 2f), l.position)
        assertEquals(Vec3(0f, 0f, -1f), l.direction)
    }

    @Test
    fun lightColorAndIntensityMayBeFlat_andPointKindIsRecognised() {
        val c = content(entity("""{"TypeComponent":{"type":"LIGHT_POINT"},"LightComponent":{"color":{"r":0.2},"intensity":2}}"""))
        val l = c.lights.single()
        assertEquals(LightKind.POINT, l.kind)
        assertEquals(0.2f, l.color.r, 0f)
        assertEquals(2f, l.intensity, 0f)
        assertEquals(DEFAULT_LIGHT_RANGE, l.range, 0f)
    }

    @Test
    fun pointLightRangeIsRead() {
        val c = content(entity("""{"TypeComponent":{"type":"LIGHT_POINT"},"LightComponent":{"light":{"range":12.5}}}"""))
        assertEquals(12.5f, c.lights.single().range, 0f)
    }

    @Test
    fun directionFollowsRotation() {
        // 90 degrees about +Y turns -Z into -X
        val s = Math.sqrt(0.5).toFloat()
        val c = content(entity("""{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{},
            "PositionComponent":{"localRotation":{"y":$s,"w":$s}}}"""))
        val d = c.lights.single().direction
        assertEquals(-1f, d.x, 1e-5f)
        assertEquals(0f, d.y, 1e-5f)
        assertEquals(0f, d.z, 1e-5f)
    }

    @Test
    fun noEcsIsEmpty() {
        assertEquals(SceneContent.EMPTY, content("{}"))
    }

    @Test
    fun mainSceneHasTheFixtureCamera() {
        val c = content(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText())
        val cam = c.cameras.single()
        assertEquals("4", cam.entityId)
        assertEquals("Camera 4", cam.name)
        assertEquals(-23.57f, cam.position.x, 0.05f)
        assertEquals(12.32f, cam.position.y, 0.05f)
        assertEquals(-0.84f, cam.position.z, 0.05f)
        assertEquals("3", cam.lookAtId)
        assertEquals(1f, cam.near, 0f)
        assertEquals(100f, cam.far, 0f)
        assertEquals(67f, cam.fieldOfView, 0f)
        assertEquals(Vec3(0f, 0f, 0f), c.entityPositions["3"])
        assertTrue(c.lights.isEmpty())
        assertEquals(3, c.models.size)
    }

    @Test
    fun mundusLightsFaceTheirHandles() {
        val c = content(File("src/test/testData/project/Lights/scenes/Mundus Lights.scene").readText())
        assertEquals(setOf("0", "3"), c.handleIds)
        val byId = c.lights.associateBy { it.entityId }
        val directional = byId["1"]!!
        assertEquals(LightKind.DIRECTIONAL, directional.kind)
        assertEquals(Vec3(0f, 10f, 0f), directional.position)
        assertEquals(Vec3(0f, -1f, 0f), directional.direction)
        assertEquals("0", directional.lookAtId)
        val spot = byId["4"]!!
        assertEquals(LightKind.SPOT, spot.kind)
        assertEquals(Vec3(0f, 5f, 0f), spot.position)
        assertEquals(Vec3(0f, -1f, 0f), spot.direction)
        assertEquals("3", spot.lookAtId)
    }

    @Test
    fun aLightWithAMissingTargetFacesAlongItsRotation() {
        val s = Math.sqrt(0.5).toFloat()
        val c = content(entity("""{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{},
            "PositionComponent":{"lookAtId":99,"localRotation":{"y":$s,"w":$s}}}"""))
        val l = c.lights.single()
        assertEquals("99", l.lookAtId)
        assertEquals(-1f, l.direction.x, 1e-5f)
        assertEquals(0f, l.direction.y, 1e-5f)
        assertEquals(0f, l.direction.z, 1e-5f)
    }

    @Test
    fun aLightAtItsTargetFacesAlongItsRotation() {
        val c = content("""{"ecs":{"entities":{
            "1":{"components":{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{},
                "PositionComponent":{"localPosition":{"x":3}}}},
            "2":{"components":{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{},
                "PositionComponent":{"lookAtId":1,"localPosition":{"x":3}}}}}}}""")
        assertEquals(Vec3(0f, 0f, -1f), c.lights.single { it.entityId == "2" }.direction)
    }

    @Test
    fun aPointLightIgnoresItsLookAtTarget() {
        val c = content("""{"ecs":{"entities":{
            "0":{"components":{"TypeComponent":{"type":"HANDLE"},"PositionComponent":{}}},
            "1":{"components":{"TypeComponent":{"type":"LIGHT_POINT"},"LightComponent":{},
                "PositionComponent":{"lookAtId":0,"localPosition":{"y":10}}}}}}}""")
        assertEquals(Vec3(0f, 0f, -1f), c.lights.single().direction)
    }

    @Test
    fun cameraWithoutCameraObjectGetsDefaults() {
        val cam = content(entity("""{"CameraComponent":{},"PositionComponent":{"localPosition":{"x":1,"y":2,"z":3}}}""")).cameras.single()
        assertEquals(Vec3(1f, 2f, 3f), cam.position)
        assertEquals(Vec3(0f, 0f, -1f), cam.direction)
        assertEquals(DEFAULT_CAMERA_NEAR, cam.near, 0f)
        assertEquals(DEFAULT_CAMERA_FAR, cam.far, 0f)
        assertEquals(DEFAULT_CAMERA_FOV, cam.fieldOfView, 0f)
        assertEquals("7", cam.name)
        assertNull(cam.lookAtId)
    }

    @Test
    fun cameraWithoutLookAtKeepsViewPoint() {
        val cam = content(entity("""{"CameraComponent":{"camera":{"viewPointPosition":{"x":1,"y":0,"z":0}}},
            "PositionComponent":{"lookAtId":-1}}""")).cameras.single()
        assertNull(cam.lookAtId)
        assertEquals(Vec3(1f, 0f, 0f), cam.direction)
    }

    @Test
    fun cameraPositionFallsBackToTheCameraObject() {
        val cam = content(entity("""{"CameraComponent":{"camera":{"position":{"x":4,"y":5,"z":6}}}}""")).cameras.single()
        assertEquals(Vec3(4f, 5f, 6f), cam.position)
    }

    @Test
    fun camerasAreNeitherLightsNorModels() {
        val c = content(entity("""{"TypeComponent":{"type":"CAMERA"},"CameraComponent":{"camera":{}},
            "RenderComponent":{"renderable":{"class":"x"}}}"""))
        assertEquals(1, c.cameras.size)
        assertTrue(c.lights.isEmpty())
        assertTrue(c.models.isEmpty())
    }
}
