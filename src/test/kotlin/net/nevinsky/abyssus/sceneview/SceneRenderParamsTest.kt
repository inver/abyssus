package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.dto.SceneReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneRenderParamsTest {
    private fun scene(json: String) = SceneReader.parse(json)

    private val full = """{"ambientLightEnabled":true,"ambientLight":{"color":{"r":1.0,"g":0.5,"b":0.0,"a":1.0},"intensity":0.5},
        "fogEnabled":true,"fog":{"color":{"r":0.2,"g":0.3,"b":0.4,"a":1.0},"density":0.01,"gradient":1.5}}"""

    @Test
    fun ambientIsColorTimesIntensity() {
        val p = SceneRenderParams.from(scene(full), CameraParams.DEFAULT)
        assertEquals(Rgba(0.5f, 0.25f, 0f, 1f), p.ambient)
    }

    @Test
    fun fogDrivesClearColorAndEquation() {
        val p = SceneRenderParams.from(scene(full), CameraParams.DEFAULT)
        assertEquals(Rgba(0.2f, 0.3f, 0.4f, 1f), p.clear)
        val fog = p.fog!!
        assertEquals(0f, fog.near, 0f)
        assertEquals(200f, fog.far, 1e-3f)
        assertEquals(1.5f, fog.exponent, 0f)
    }

    @Test
    fun disabledFeaturesAreOmitted() {
        val p = SceneRenderParams.from(scene("""{"ambientLightEnabled":false,"fogEnabled":false}"""), CameraParams.DEFAULT)
        assertNull(p.ambient)
        assertNull(p.fog)
        assertEquals(SceneRenderParams.DEFAULT_CLEAR, p.clear)
    }

    @Test
    fun missingObjectsDoNotThrow() {
        val p = SceneRenderParams.from(scene("""{"ambientLightEnabled":true,"fogEnabled":true}"""), CameraParams.DEFAULT)
        assertNull(p.ambient)
        assertNull(p.fog)
    }

    @Test
    fun zeroOrNullFogDensityMeansNoFog() {
        for (density in listOf("0", "0.0", "null", "-1")) {
            val json = """{"fogEnabled":true,"fog":{"color":{"r":1,"g":1,"b":1,"a":1},"density":$density}}"""
            val p = SceneRenderParams.from(scene(json), CameraParams.DEFAULT)
            assertNull("density $density", p.fog)
            assertEquals(SceneRenderParams.DEFAULT_CLEAR, p.clear)
        }
    }

    @Test
    fun invalidFogGradientFallsBackToOne() {
        val json = """{"fogEnabled":true,"fog":{"color":{"r":1,"g":1,"b":1,"a":1},"density":0.5,"gradient":0}}"""
        assertEquals(1f, SceneRenderParams.from(scene(json), CameraParams.DEFAULT).fog!!.exponent, 0f)
    }

    @Test
    fun missingIntensityDefaultsToOne() {
        val json = """{"ambientLightEnabled":true,"ambientLight":{"color":{"r":0.4,"g":0.4,"b":0.4,"a":1}}}"""
        assertEquals(Rgba(0.4f, 0.4f, 0.4f, 1f), SceneRenderParams.from(scene(json), CameraParams.DEFAULT).ambient)
    }

    @Test
    fun parsesMainCamera() {
        val abss = """{"mainCamera":{"viewPointPosition":{"x":-0.5,"y":0.0,"z":-0.5},"position":{"x":4.0,"y":3.0,"z":6.0},
            "far":100.0,"near":1.0,"fieldOfView":67.0},"name":"Untitled"}"""
        val cam = MainCamera.parse(abss)!!
        assertEquals(Vec3(4f, 3f, 6f), cam.position)
        assertEquals(100f, cam.far, 0f)
        assertEquals(67f, cam.fieldOfView, 0f)
        val d = cam.direction
        assertEquals(1f, kotlin.math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z), 1e-5f)
    }

    @Test
    fun mainCameraParseIsNullForGarbageOrMissingCamera() {
        assertNull(MainCamera.parse("not json"))
        assertNull(MainCamera.parse("[]"))
        assertNull(MainCamera.parse("""{"name":"x"}"""))
        assertNull(MainCamera.parse("""{"mainCamera":{"position":{"x":1,"y":2,"z":3}}}"""))
    }

    @Test
    fun zeroLengthViewDirectionIsRejected() {
        val abss = """{"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":0},"position":{"x":1,"y":2,"z":3}}}"""
        assertNull(MainCamera.parse(abss))
    }

    @Test
    fun defaultCameraIsUsable() {
        assertNotNull(CameraParams.DEFAULT)
        assertTrue(CameraParams.DEFAULT.far > CameraParams.DEFAULT.near)
    }
}
