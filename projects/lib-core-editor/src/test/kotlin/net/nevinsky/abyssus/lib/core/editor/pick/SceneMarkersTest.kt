/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.arrange.LineHeights
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementAlong
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.Rgba

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.lib.core.editor.parseScene
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

    private fun main() = sceneContentOf(parseScene(File("src/test/testData/project/Tree/scenes/Main Scene.scene").readText()))

    private fun content(entities: String) = sceneContentOf(parseScene("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{$entities}}}"""))

    private fun light(id: String, type: String, x: Float = 0f) =
        """"$id":{"components":{"TypeComponent":{"type":"$type"},"LightComponent":{"light":{}},"PositionComponent":{"localPosition":{"x":$x}}}}"""

    private fun ray(from: Vector3, to: Vector3) = Ray(from, Vector3(to).sub(from).nor())

    @Test
    fun aRayThroughTheCameraBodyPicksTheCamera() {
        val c = main()
        val p = c.cameras.single().position
        val targets = SceneMarkers().targets(c)
        val r = ray(Vector3(p.x, p.y, p.z + 10f), Vector3(p.x, p.y, p.z))
        assertEquals("4", ScenePicker().pick(r, targets, emptyList(), 1000f))
    }

    @Test
    fun aRayThroughALightMarkerPicksTheLight() {
        val c = content(light("9", "LIGHT_POINT", x = 4f))
        val r = ray(Vector3(4f, 0f, 10f), Vector3(4f, 0f, 0f))
        assertEquals("9", ScenePicker().pick(r, SceneMarkers().targets(c), emptyList(), 100f))
    }

    @Test
    fun theNearestTargetStillWins() {
        val c = content(light("9", "LIGHT_POINT"))
        val nearer = BoxTarget("model", BoundingBox(Vector3(-1f, -1f, 3f), Vector3(1f, 1f, 5f)))
        val r = ray(Vector3(0f, 0f, 10f), Vector3(0f, 0f, 0f))
        assertEquals("model", ScenePicker().pick(r, SceneMarkers().targets(c) + nearer, emptyList(), 100f))
    }

    @Test
    fun theViewCameraHasNoMarkerTarget() {
        val c = main()
        // only the scene's lights (7 and 8) remain pickable markers
        assertEquals(listOf("7", "8"), SceneMarkers().targets(c, skipCamera = "4").map { it.entityId })
        assertNull(ScenePicker().pick(ray(Vector3(0f, 0f, 10f), Vector3(0f, 0f, 0f)), SceneMarkers().targets(c, "4"), emptyList(), 100f))
    }

    @Test
    fun aCameraDrawsABodyAndAFrustum() {
        val out = Recorder()
        SceneMarkers().draw(out, main(), 1.5f)
        val skipped = Recorder()
        SceneMarkers().draw(skipped, main(), 1.5f, skipCamera = "4")
        // the camera: 12 frustum edges, 12 body edges and 4 lens lines; the rest are the two lights' markers
        assertEquals(28, out.lines.size - skipped.lines.size)
        assertEquals(26, skipped.lines.size)
    }

    @Test
    fun directionalAndSpotLightsHaveADirectionLineButPointLightsDoNot() {
        fun lines(type: String): Int = Recorder().also { SceneMarkers().draw(it, content(light("1", type)), 1f) }.lines.size
        assertEquals(12, lines("LIGHT_POINT"))
        assertEquals(13, lines("LIGHT_DIRECTIONAL"))
        assertEquals(13, lines("LIGHT_SPOT"))
    }

    @Test
    fun boundsOfFindsCamerasAndLightsOnly() {
        val c = content(light("1", "LIGHT_POINT") + "," + """"2":{"components":{"CameraComponent":{"camera":{}}}}""")
        assertTrue(SceneMarkers().boundsOf(c, "1") != null)
        assertTrue(SceneMarkers().boundsOf(c, "2") != null)
        assertNull(SceneMarkers().boundsOf(c, "3"))
    }

    @Test
    fun aPathDrawsItsLineAndACrossWithAPostForEachCopy() {
        val out = Recorder()
        val path = arrangementAlong(listOf(Vec3(0f, 1f, 0f), Vec3(20f, 1f, 0f)))
        SceneMarkers().drawPath(out, path, GroundHeight { _, _, _ -> 5f }, handles = false)
        // the line, then three copies (X 0, 10 and 20) of three lines each
        assertEquals(10, out.lines.size)
        assertEquals(Vec3(0f, 1f, 0f) to Vec3(20f, 1f, 0f), out.lines[0])
        val posts = out.lines.filter { (a, b) -> a.x == b.x && a.z == b.z }
        assertEquals(listOf(0f, 10f, 20f), posts.map { it.first.x })
        assertTrue(posts.all { (a, b) -> a.y == 5f && b.y > 5f })
    }

    @Test
    fun aClosedPathDrawsTheClosingSegment() {
        val out = Recorder()
        val path = arrangementAlong(listOf(Vec3(0f, 0f, 0f), Vec3(20f, 0f, 0f), Vec3(20f, 0f, 20f)), closed = true)
        SceneMarkers().drawPath(out, path, LineHeights(), handles = false)
        assertEquals(Vec3(20f, 0f, 20f) to Vec3(0f, 0f, 0f), out.lines[2])
    }

    @Test
    fun handlesAddADiamondOnEachPointAndAMiddleOne() {
        val plain = Recorder().also {
            SceneMarkers().drawPath(it, arrangementAlong(listOf(Vec3(0f, 0f, 0f), Vec3(20f, 0f, 0f))), LineHeights(), handles = false)
        }
        val withHandles = Recorder().also {
            SceneMarkers().drawPath(it, arrangementAlong(listOf(Vec3(0f, 0f, 0f), Vec3(20f, 0f, 0f))), LineHeights(), handles = true)
        }
        // an octahedron has twelve edges: two points and one middle
        assertEquals(3 * 12, withHandles.lines.size - plain.lines.size)
    }
}
