/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.parseScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SceneMarkersTest {
    private class Recorder : LineSink {
        val lines = mutableListOf<Pair<Vec3, Vec3>>()
        override fun line(from: Vec3, to: Vec3, color: Rgba) {
            lines += from to to
        }
    }

    private fun main() = SceneContent.of(parseScene(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()))

    private fun content(entities: String) = SceneContent.of(parseScene("""{"ecs":{"entities":{$entities}}}"""))

    private fun light(id: String, type: String, x: Float = 0f) =
        """"$id":{"components":{"TypeComponent":{"type":"$type"},"LightComponent":{"light":{}},"PositionComponent":{"localPosition":{"x":$x}}}}"""

    private fun ray(from: Vector3, to: Vector3) = Ray(from, Vector3(to).sub(from).nor())

    @Test
    fun aRayThroughTheCameraBodyPicksTheCamera() {
        val c = main()
        val p = c.cameras.single().position
        val targets = SceneMarkers.targets(c)
        val r = ray(Vector3(p.x, p.y, p.z + 10f), Vector3(p.x, p.y, p.z))
        assertEquals("4", ScenePicker.pick(r, targets, emptyList(), 1000f))
    }

    @Test
    fun aRayThroughALightMarkerPicksTheLight() {
        val c = content(light("9", "LIGHT_POINT", x = 4f))
        val r = ray(Vector3(4f, 0f, 10f), Vector3(4f, 0f, 0f))
        assertEquals("9", ScenePicker.pick(r, SceneMarkers.targets(c), emptyList(), 100f))
    }

    @Test
    fun theNearestTargetStillWins() {
        val c = content(light("9", "LIGHT_POINT"))
        val nearer = BoxTarget("model", BoundingBox(Vector3(-1f, -1f, 3f), Vector3(1f, 1f, 5f)))
        val r = ray(Vector3(0f, 0f, 10f), Vector3(0f, 0f, 0f))
        assertEquals("model", ScenePicker.pick(r, SceneMarkers.targets(c) + nearer, emptyList(), 100f))
    }

    @Test
    fun theViewCameraHasNoMarkerTarget() {
        val c = main()
        assertEquals(listOf("7"), SceneMarkers.targets(c, skipCamera = "4").map { it.entityId })
        assertNull(ScenePicker.pick(ray(Vector3(0f, 0f, 10f), Vector3(0f, 0f, 0f)), SceneMarkers.targets(c, "4"), emptyList(), 100f))
    }

    @Test
    fun aCameraDrawsABodyAndAFrustum() {
        val out = Recorder()
        SceneMarkers.draw(out, main(), 1.5f)
        // 12 frustum edges, 12 body edges, 4 lens lines and the fixture light's 13 marker lines
        assertEquals(41, out.lines.size)
        val skipped = Recorder()
        SceneMarkers.draw(skipped, main(), 1.5f, skipCamera = "4")
        assertEquals(13, skipped.lines.size)
    }

    @Test
    fun directionalAndSpotLightsHaveADirectionLineButPointLightsDoNot() {
        fun lines(type: String): Int = Recorder().also { SceneMarkers.draw(it, content(light("1", type)), 1f) }.lines.size
        assertEquals(12, lines("LIGHT_POINT"))
        assertEquals(13, lines("LIGHT_DIRECTIONAL"))
        assertEquals(13, lines("LIGHT_SPOT"))
    }

    @Test
    fun boundsOfFindsCamerasAndLightsOnly() {
        val c = content(light("1", "LIGHT_POINT") + "," + """"2":{"components":{"CameraComponent":{"camera":{}}}}""")
        assertTrue(SceneMarkers.boundsOf(c, "1") != null)
        assertTrue(SceneMarkers.boundsOf(c, "2") != null)
        assertNull(SceneMarkers.boundsOf(c, "3"))
    }
}
