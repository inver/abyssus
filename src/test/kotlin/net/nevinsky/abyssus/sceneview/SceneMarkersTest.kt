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

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.dto.SceneReader
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

    private fun main() = SceneContent.of(SceneReader.parse(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()))

    private fun content(entities: String) = SceneContent.of(SceneReader.parse("""{"ecs":{"entities":{$entities}}}"""))

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
    fun theLookedThroughCameraIsNotPickable() {
        val c = main()
        assertTrue(SceneMarkers.targets(c, skipCamera = "4").isEmpty())
        assertNull(ScenePicker.pick(ray(Vector3(0f, 0f, 10f), Vector3(0f, 0f, 0f)), SceneMarkers.targets(c, "4"), emptyList(), 100f))
    }

    @Test
    fun aCameraDrawsABodyAndAFrustum() {
        val out = Recorder()
        SceneMarkers.draw(out, main(), 1.5f)
        // 12 frustum edges, 12 body edges and 4 lens lines
        assertEquals(28, out.lines.size)
        val skipped = Recorder()
        SceneMarkers.draw(skipped, main(), 1.5f, skipCamera = "4")
        assertTrue(skipped.lines.isEmpty())
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
