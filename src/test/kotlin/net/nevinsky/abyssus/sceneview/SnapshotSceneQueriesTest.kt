/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Quat
import net.nevinsky.abyssus.editor.content.PlacementTransform
import net.nevinsky.abyssus.editor.content.AssetPlacement

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.sceneview.gizmo.DragResult
import net.nevinsky.abyssus.sceneview.gizmo.GizmoAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The picking, drop and gizmo queries over a hand-built frame: no renderer, no GL. */
class SnapshotSceneQueriesTest {
    private val width = 800
    private val height = 600

    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private val unit = BoundingBox(Vector3(-1f, 0f, -1f), Vector3(1f, 2f, 1f))

    private fun at(x: Float, y: Float, z: Float) = Matrix4().setToTranslation(x, y, z)

    /** Looks along -Z at the height 1 from z 10, so a click on the centre of the view goes through the middle of boxes at x 0. */
    private fun camera() = PerspectiveCamera(67f, width.toFloat(), height.toFloat()).also {
        it.position.set(0f, 1f, 10f)
        it.lookAt(0f, 1f, 0f)
        it.near = 0.1f
        it.far = 100f
        it.update()
    }

    private fun placement(id: String, x: Float, y: Float, z: Float) =
        AssetPlacement(id, "model", PlacementTransform(Vec3(x, y, z), Quat.IDENTITY, Vec3(1f, 1f, 1f)))

    private class Fixture(val state: SceneViewState, val queries: SnapshotSceneQueries)

    private fun fixture(boxes: List<SnapshotBox>, content: SceneContent = SceneContent.EMPTY, terrains: List<TerrainTarget> = emptyList()): Fixture {
        val state = SceneViewState()
        val frame = FrameSnapshot(FrameSnapshot.copyOf(camera()), boxes, terrains, 7L)
        return Fixture(state, SnapshotSceneQueries({ frame }, state) { content })
    }

    @Test
    fun theNearestBoxUnderTheCursorIsPicked() {
        val f = fixture(listOf(FrameSnapshot.boxOf("far", unit, at(0f, 0f, -6f)), FrameSnapshot.boxOf("near", unit, at(0f, 0f, 0f))))
        assertEquals("near", f.queries.pick(width / 2, height / 2, width, height))
        assertNull(f.queries.pick(2, 2, width, height))
    }

    @Test
    fun noFrameYetMeansNothingIsFound() {
        val state = SceneViewState()
        val queries = SnapshotSceneQueries({ null }, state) { SceneContent.EMPTY }
        assertNull(queries.pick(10, 10, width, height))
        assertNull(queries.rayAt(10, 10, width, height))
        assertNull(queries.groundBelow("x"))
        assertNull(queries.gizmoHandles(height))
        assertEquals(0L, queries.drawnVersion)
    }

    @Test
    fun anObjectAboveAnotherRestsOnItsTop() {
        val f = fixture(listOf(FrameSnapshot.boxOf("floor", unit, at(0f, 0f, 0f)), FrameSnapshot.boxOf("above", unit, at(0f, 5f, 0f))))
        assertEquals(2f, f.queries.groundBelow("above")!!, 1e-5f)
        assertEquals(5f, f.queries.lowestPoint("above")!!, 1e-5f)
        assertNull("nothing under the floor", f.queries.groundBelow("floor"))
        assertNull(f.queries.groundBelow("missing"))
    }

    @Test
    fun theViewCameraAndTerrainsCannotDrop() {
        val content = SceneContent(models = listOf(placement("a", 0f, 5f, 0f)))
        val f = fixture(listOf(FrameSnapshot.boxOf("floor", unit, at(0f, 0f, 0f)), FrameSnapshot.boxOf("a", unit, at(0f, 5f, 0f))), content)
        f.state.viewCamera = "a"
        assertNull(f.queries.groundBelow("a"))
    }

    @Test
    fun aPreviewMovesTheBoxForLaterQueries() {
        val f = fixture(listOf(FrameSnapshot.boxOf("floor", unit, at(0f, 0f, 0f)), FrameSnapshot.boxOf("a", unit, at(0f, 5f, 0f))), SceneContent(models = listOf(placement("a", 0f, 5f, 0f))))
        assertEquals(5f, f.queries.lowestPoint("a")!!, 1e-5f)
        f.state.preview = mapOf("a" to DragResult(PlacementTransform(Vec3(0f, 9f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f)), null))
        assertEquals(9f, f.queries.lowestPoint("a")!!, 1e-5f)
    }

    @Test
    fun theBoxesArePutTogetherOncePerSnapshotAndPreview() {
        val f = fixture(listOf(FrameSnapshot.boxOf("floor", unit, at(0f, 0f, 0f)), FrameSnapshot.boxOf("a", unit, at(0f, 5f, 0f))), SceneContent(models = listOf(placement("a", 0f, 5f, 0f))))
        repeat(5) {
            f.queries.lowestPoint("a")
            f.queries.groundBelow("a")
            f.queries.pick(width / 2, height / 2, width, height)
        }
        assertEquals(1, f.queries.targetBuilds)
        f.state.preview = mapOf("a" to DragResult(PlacementTransform(Vec3(0f, 6f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f)), null))
        f.queries.lowestPoint("a")
        f.queries.groundBelow("a")
        assertEquals(2, f.queries.targetBuilds)
    }

    @Test
    fun aGizmoHandleUnderTheCursorIsHit() {
        val content = SceneContent(models = listOf(placement("a", 0f, 1f, 0f)))
        val f = fixture(listOf(FrameSnapshot.boxOf("a", unit, at(0f, 1f, 0f))), content)
        f.state.selectedId = "a"
        val handles = f.queries.gizmoHandles(height)
        assertNotNull(handles)
        val tip = handles!!.tip(GizmoAxis.X)
        val screen = Vector3(tip.x, tip.y, tip.z).also { camera().project(it, 0f, 0f, width.toFloat(), height.toFloat()) }
        val x = screen.x.toInt()
        val y = height - screen.y.toInt()
        assertEquals(GizmoAxis.X, f.queries.gizmoHit(x, y, width, height))
        assertNull(f.queries.gizmoHit(2, 2, width, height))
        assertNotNull(f.queries.beginDrag(GizmoAxis.X, x, y, width, height))
        f.state.selectedId = null
        assertNull(f.queries.gizmoHandles(height))
        assertTrue(f.queries.drawnVersion == 7L)
    }
}
