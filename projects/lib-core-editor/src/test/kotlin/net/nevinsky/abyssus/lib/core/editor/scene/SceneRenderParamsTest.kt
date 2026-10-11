/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.lib.core.editor.content.LightKind

import net.nevinsky.abyssus.lib.core.editor.parseScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneRenderParamsTest {
    private fun scene(json: String) = parseScene(json)

    @Test
    fun spotlightBeamValuesReachRenderContentFromTheLightWrapper() {
        for (fields in listOf("", "\"coneAngle\":60,\"edgeSoftness\":0.25,")) {
            val values = """{$fields"future":1.23400}"""
            val light = """{"light":$values}"""
            val text = """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"4":{"components":{"TypeComponent":{"type":"LIGHT_SPOT"},"LightComponent":$light}}}}}"""
            val p = renderParamsOf(scene(text), CameraParams.DEFAULT).content.lights.single()
            assertEquals(LightKind.SPOT, p.kind)
            assertEquals(if (fields.isEmpty()) 45f else 60f, p.coneAngle, 0f)
            assertEquals(if (fields.isEmpty()) 0.2f else 0.25f, p.edgeSoftness, 0f)
        }
    }


    private val full = """{"format":"abyssus","formatVersion":1,"ambientLightEnabled":true,"ambientLight":{"color":{"r":1.0,"g":0.5,"b":0.0,"a":1.0},"intensity":0.5},
        "fogEnabled":true,"fog":{"color":{"r":0.2,"g":0.3,"b":0.4,"a":1.0},"density":0.01,"gradient":1.5}}"""

    @Test
    fun ambientIsColorTimesIntensity() {
        val p = renderParamsOf(scene(full), CameraParams.DEFAULT)
        assertEquals(Rgba(0.5f, 0.25f, 0f, 1f), p.ambient)
    }

    @Test
    fun fogDrivesClearColorAndEquation() {
        val p = renderParamsOf(scene(full), CameraParams.DEFAULT)
        assertEquals(Rgba(0.2f, 0.3f, 0.4f, 1f), p.clear)
        val fog = p.fog!!
        assertEquals(0.01f, fog.density, 0f)
        assertEquals(1.5f, fog.gradient, 0f)
    }

    @Test
    fun disabledFeaturesAreOmitted() {
        val p = renderParamsOf(scene("""{"format":"abyssus","formatVersion":1,"ambientLightEnabled":false,"fogEnabled":false}"""), CameraParams.DEFAULT)
        assertNull(p.ambient)
        assertNull(p.fog)
        assertEquals(SceneRenderParams.DEFAULT_CLEAR, p.clear)
    }

    @Test
    fun missingObjectsDoNotThrow() {
        val p = renderParamsOf(scene("""{"format":"abyssus","formatVersion":1,"ambientLightEnabled":true,"fogEnabled":true}"""), CameraParams.DEFAULT)
        assertNull(p.ambient)
        assertNull(p.fog)
    }

    @Test
    fun zeroOrNullFogDensityMeansNoFog() {
        for (density in listOf("0", "0.0", "null", "-1")) {
            val json = """{"format":"abyssus","formatVersion":1,"fogEnabled":true,"fog":{"color":{"r":1,"g":1,"b":1,"a":1},"density":$density}}"""
            val p = renderParamsOf(scene(json), CameraParams.DEFAULT)
            assertNull("density $density", p.fog)
            assertEquals(SceneRenderParams.DEFAULT_CLEAR, p.clear)
        }
    }

    @Test
    fun invalidFogGradientFallsBackToOne() {
        val json = """{"format":"abyssus","formatVersion":1,"fogEnabled":true,"fog":{"color":{"r":1,"g":1,"b":1,"a":1},"density":0.5,"gradient":0}}"""
        assertEquals(1f, renderParamsOf(scene(json), CameraParams.DEFAULT).fog!!.gradient, 0f)
    }

    @Test
    fun missingIntensityDefaultsToOne() {
        val json = """{"format":"abyssus","formatVersion":1,"ambientLightEnabled":true,"ambientLight":{"color":{"r":0.4,"g":0.4,"b":0.4,"a":1}}}"""
        assertEquals(Rgba(0.4f, 0.4f, 0.4f, 1f), renderParamsOf(scene(json), CameraParams.DEFAULT).ambient)
    }

    @Test
    fun parsesMainCamera() {
        val abss = """{"format":"abyssus","formatVersion":1,"mainCamera":{"viewPointPosition":{"x":-0.5,"y":0.0,"z":-0.5},"position":{"x":4.0,"y":3.0,"z":6.0},
            "far":100.0,"near":1.0,"fieldOfView":67.0},"name":"Untitled"}"""
        val cam = MainCamera().parse(abss)!!
        assertEquals(Vec3(4f, 3f, 6f), cam.position)
        assertEquals(100f, cam.far, 0f)
        assertEquals(67f, cam.fieldOfView, 0f)
        val d = cam.direction
        assertEquals(1f, kotlin.math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z), 1e-5f)
    }

    @Test
    fun mainCameraParseIsNullForGarbageOrMissingCamera() {
        assertNull(MainCamera().parse("not json"))
        assertNull(MainCamera().parse("[]"))
        assertNull(MainCamera().parse("""{"name":"x"}"""))
        assertNull(MainCamera().parse("""{"format":"abyssus","formatVersion":1,"mainCamera":{"position":{"x":1,"y":2,"z":3}}}"""))
    }

    @Test
    fun zeroLengthViewDirectionIsRejected() {
        val abss = """{"format":"abyssus","formatVersion":1,"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":0},"position":{"x":1,"y":2,"z":3}}}"""
        assertNull(MainCamera().parse(abss))
    }

    @Test
    fun defaultCameraIsUsable() {
        assertNotNull(CameraParams.DEFAULT)
        assertTrue(CameraParams.DEFAULT.far > CameraParams.DEFAULT.near)
    }

    @Test
    fun shaderCoefficientMatchesMundusFogAtCharacteristicDistance() {
        for (gradient in listOf(0.5f, 1f, 1.5f, 3f)) {
            val fog = FogParams(Rgba(1f, 1f, 1f, 1f), 0.02f, gradient)
            val d = 1f / fog.density
            val expected = 1f - kotlin.math.exp(-1f)
            assertEquals(expected, fog.amount(d), 1e-5f)
            assertEquals(expected, fog.shaderCoefficient * d * d, 1e-4f)
        }
    }

    @Test
    fun fogAmountGrowsWithDistanceAndStaysInRange() {
        val fog = FogParams(Rgba(1f, 1f, 1f, 1f), 0.01f, 1.5f)
        assertEquals(0f, fog.amount(0f), 0f)
        assertTrue(fog.amount(50f) < fog.amount(150f))
        assertTrue(fog.amount(1e9f) <= 1f)
    }

    @Test
    fun nullNearKeepsTheRestOfTheCamera() {
        val abss = """{"format":"abyssus","formatVersion":1,"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":-1},"position":{"x":1,"y":2,"z":3},"near":null,"far":50.0}}"""
        val cam = MainCamera().parse(abss)!!
        assertEquals(Vec3(1f, 2f, 3f), cam.position)
        assertEquals(CameraParams.DEFAULT.near, cam.near, 0f)
        assertEquals(50f, cam.far, 0f)
    }

    @Test
    fun nearNotBelowFarFallsBackToDefaultClipRange() {
        for ((near, far) in listOf(10 to 10, 100 to 1)) {
            val abss = """{"format":"abyssus","formatVersion":1,"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":-1},"position":{"x":1,"y":2,"z":3},"near":$near,"far":$far}}"""
            val cam = MainCamera().parse(abss)!!
            assertEquals(CameraParams.DEFAULT.near, cam.near, 0f)
            assertEquals(CameraParams.DEFAULT.far, cam.far, 0f)
        }
    }

    @Test
    fun contentAndProjectDirFlowThrough() {
        val json = """{"format":"abyssus","formatVersion":1,"skyboxEnabled":true,"skyboxName":"sky","ecs":{"entities":{"1":{"components":
            {"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"m"}}}}}}}}"""
        val dir = java.io.File("proj")
        val p = renderParamsOf(scene(json), CameraParams.DEFAULT, dir)
        assertEquals(listOf("m"), p.content.models.map { it.assetName })
        assertEquals("sky", p.content.skybox)
        assertEquals(dir, p.projectDir)
    }
}
