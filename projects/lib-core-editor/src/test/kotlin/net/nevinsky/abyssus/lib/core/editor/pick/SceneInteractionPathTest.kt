/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementAlong
import net.nevinsky.abyssus.lib.core.editor.content.ArrangementPlacement
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val W = 800
private const val H = 600

/** Drawing and editing a path arrangement over a hand-built frame: a flat terrain, real queries, no renderer and no GL. */
class SceneInteractionPathTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private val tree = RenderAsset("MODEL", "tree")

    private fun camera() = PerspectiveCamera(67f, W.toFloat(), H.toFloat()).also {
        it.position.set(50f, 60f, 110f)
        it.lookAt(50f, 0f, 40f)
        it.near = 0.1f
        it.far = 300f
        it.update()
    }

    private class Fixture(
        val state: SceneViewState,
        val queries: SnapshotSceneQueries,
        val interaction: SceneInteraction,
        val camera: PerspectiveCamera,
    ) {
        val created = mutableListOf<Triple<RenderAsset, List<Vec3>, Boolean>>()
        val edits = mutableListOf<Pair<String, List<Vec3>>>()
        var accept = true

        /** The Swing pixel of the world point ([x], [y], [z]). */
        fun pixelOf(x: Float, z: Float, y: Float = 0f): Pair<Int, Int> {
            val v = Vector3(x, y, z)
            camera.project(v, 0f, 0f, W.toFloat(), H.toFloat())
            return v.x.toInt() to (H - v.y.toInt())
        }

        fun click(x: Float, z: Float, count: Int = 1) {
            val (px, py) = pixelOf(x, z)
            interaction.pressed(px, py, true)
            interaction.released(px, py, true, count)
        }
    }

    private fun fixture(arrangement: Boolean = false, copy: Boolean = false, closed: Boolean = false, points: List<Vec3> = line()): Fixture {
        val state = SceneViewState()
        val content = SceneContent(
            terrains = listOf(AssetPlacement("1", "terrain", PlacementTransform.IDENTITY)),
            arrangements = if (arrangement) listOf(ArrangementPlacement("5", arrangementAlong(points, closed = closed))) else emptyList(),
        )
        val boxes = if (copy) {
            listOf(snapshotBoxOf("12", BoundingBox(Vector3(-2f, -2f, -2f), Vector3(2f, 2f, 2f)), Matrix4().setToTranslation(50f, 0f, 50f)))
        } else emptyList()
        val terrain = TerrainTarget("1", TerrainData(3, FloatArray(9), 100, 1f), Matrix4())
        val frame = FrameSnapshot(camera(), boxes, listOf(snapshotTerrainOf(terrain)), 7L)
        val queries = SnapshotSceneQueries({ frame }, state) { content }
        val interaction = SceneInteraction(state, queries, OrbitCamera(Vec3(50f, 0f, 50f), 60f, 0f, 0.6f)).also {
            it.size = ViewSize(W, H, W, H)
        }
        return Fixture(state, queries, interaction, frame.camera).also { f ->
            interaction.onArrangementCreate = { asset, p, closedLine -> f.created += Triple(asset, p, closedLine); f.accept }
            interaction.onArrangementEdit = { id, p -> f.edits += id to p; f.accept }
        }
    }

    private fun line() = listOf(Vec3(20f, 0f, 50f), Vec3(80f, 0f, 50f))

    private fun assertNear(x: Float, z: Float, p: Vec3) {
        assertEquals(x, p.x, 0.6f)
        assertEquals(0f, p.y, 0.01f)
        assertEquals(z, p.z, 0.6f)
    }

    @Test
    fun clicksOnTheTerrainAddPoints() {
        val f = fixture()
        f.interaction.startDrawing(tree)
        f.click(20f, 50f)
        f.click(50f, 60f)
        f.click(80f, 50f)
        val points = f.interaction.drawing!!.points
        assertEquals(3, points.size)
        assertNear(20f, 50f, points[0])
        assertNear(50f, 60f, points[1])
        assertNear(80f, 50f, points[2])
    }

    @Test
    fun aClickWhereNoTerrainIsAddsNothing() {
        val f = fixture()
        f.interaction.startDrawing(tree)
        f.click(-20f, 50f)
        assertTrue(f.interaction.drawing!!.points.isEmpty())
        assertTrue(f.created.isEmpty())
    }

    @Test
    fun backspaceRemovesTheLastPoint() {
        val f = fixture()
        f.interaction.startDrawing(tree)
        f.click(20f, 50f)
        f.click(50f, 60f)
        f.click(80f, 50f)
        f.interaction.backspace()
        assertEquals(2, f.interaction.drawing!!.points.size)
        assertNear(50f, 60f, f.interaction.drawing!!.points.last())
    }

    @Test
    fun enterFinishesAnOpenLineAndEndsDrawing() {
        val f = fixture()
        f.interaction.startDrawing(tree)
        f.click(20f, 50f)
        f.click(80f, 50f)
        f.interaction.enter()
        assertEquals(1, f.created.size)
        assertEquals(tree, f.created[0].first)
        assertEquals(2, f.created[0].second.size)
        assertFalse(f.created[0].third)
        assertNull(f.interaction.drawing)
    }

    @Test
    fun tooFewPointsKeepDrawing() {
        val f = fixture()
        f.interaction.startDrawing(tree)
        f.click(20f, 50f)
        f.interaction.enter()
        assertTrue(f.created.isEmpty())
        assertEquals(1, f.interaction.drawing!!.points.size)
    }

    @Test
    fun aRefusedWriteKeepsTheDrawing() {
        val f = fixture()
        f.accept = false
        f.interaction.startDrawing(tree)
        f.click(20f, 50f)
        f.click(80f, 50f)
        f.interaction.enter()
        assertEquals(1, f.created.size)
        assertEquals(2, f.interaction.drawing!!.points.size)
    }

    @Test
    fun clickingTheFirstPointOfAFourPointDrawingClosesIt() {
        val f = fixture()
        f.interaction.startDrawing(tree)
        f.click(20f, 30f)
        f.click(80f, 30f)
        f.click(80f, 70f)
        f.click(20f, 70f)
        f.click(20f, 30f)
        assertEquals(1, f.created.size)
        assertEquals(4, f.created[0].second.size)
        assertTrue(f.created[0].third)
        assertNull(f.interaction.drawing)
    }

    @Test
    fun clickingTheFirstPointOfTwoPointsAddsAnotherPoint() {
        val f = fixture()
        f.interaction.startDrawing(tree)
        f.click(20f, 30f)
        f.click(80f, 30f)
        f.click(20f, 30f)
        assertTrue(f.created.isEmpty())
        assertEquals(3, f.interaction.drawing!!.points.size)
    }

    @Test
    fun aDoubleClickFinishesWithThePointsSoFar() {
        val f = fixture()
        f.interaction.startDrawing(tree)
        f.click(20f, 50f)
        f.click(80f, 50f)
        f.click(80f, 50f, count = 2)
        assertEquals(1, f.created.size)
        assertEquals(2, f.created[0].second.size)
    }

    @Test
    fun escapeCancelsTheDrawingAndWritesNothing() {
        val f = fixture()
        f.interaction.startDrawing(tree)
        f.click(20f, 50f)
        f.click(50f, 60f)
        f.click(80f, 50f)
        f.interaction.escape()
        assertNull(f.interaction.drawing)
        assertTrue(f.created.isEmpty())
        assertTrue(f.edits.isEmpty())
    }

    @Test
    fun draggingAPointHandleMovesItAndWritesOnRelease() {
        val f = fixture(arrangement = true)
        f.state.selectedId = "5"
        val (x0, y0) = f.pixelOf(80f, 50f)
        val (x1, y1) = f.pixelOf(60f, 40f)
        f.interaction.pressed(x0, y0, true)
        f.interaction.dragged(x1, y1, true)
        val shown = f.state.pathEdit!!
        assertEquals("5", shown.entityId)
        assertNear(60f, 40f, shown.points[1])
        assertNear(20f, 50f, shown.points[0])
        assertTrue(f.edits.isEmpty())
        f.interaction.released(x1, y1, true)
        assertEquals(1, f.edits.size)
        assertEquals("5", f.edits[0].first)
        assertNear(60f, 40f, f.edits[0].second[1])
    }

    @Test
    fun draggingAMiddleHandleInsertsAPoint() {
        val f = fixture(arrangement = true)
        f.state.selectedId = "5"
        val (x0, y0) = f.pixelOf(50f, 50f)
        val (x1, y1) = f.pixelOf(50f, 70f)
        f.interaction.pressed(x0, y0, true)
        f.interaction.dragged(x1, y1, true)
        f.interaction.released(x1, y1, true)
        val points = f.edits.single().second
        assertEquals(3, points.size)
        assertNear(20f, 50f, points[0])
        assertNear(50f, 70f, points[1])
        assertNear(80f, 50f, points[2])
    }

    @Test
    fun escapeDuringAPointDragRestoresAndWritesNothing() {
        val f = fixture(arrangement = true)
        f.state.selectedId = "5"
        val (x0, y0) = f.pixelOf(80f, 50f)
        val (x1, y1) = f.pixelOf(60f, 40f)
        f.interaction.pressed(x0, y0, true)
        f.interaction.dragged(x1, y1, true)
        f.interaction.escape()
        f.interaction.released(x1, y1, true)
        assertTrue(f.edits.isEmpty())
        assertNull(f.state.pathEdit)
    }

    @Test
    fun pressingAHandleWithoutMovingWritesNothing() {
        val f = fixture(arrangement = true)
        f.state.selectedId = "5"
        val (x0, y0) = f.pixelOf(80f, 50f)
        f.interaction.pressed(x0, y0, true)
        f.interaction.released(x0, y0, true)
        assertTrue(f.edits.isEmpty())
        assertNull(f.state.pathEdit)
    }

    @Test
    fun aPointCanBeRemovedOnlyWhileMoreThanTwoRemain() {
        val two = fixture(arrangement = true)
        assertFalse(two.interaction.canRemovePoint("5"))
        assertFalse(two.interaction.removePoint("5", 0))
        val three = fixture(arrangement = true, points = listOf(Vec3(20f, 0f, 50f), Vec3(50f, 0f, 60f), Vec3(80f, 0f, 50f)))
        assertTrue(three.interaction.canRemovePoint("5"))
        assertTrue(three.interaction.removePoint("5", 1))
        assertEquals(listOf(Vec3(20f, 0f, 50f), Vec3(80f, 0f, 50f)), three.edits.single().second)
    }

    @Test
    fun clickingTheLineSelectsTheArrangement() {
        val f = fixture(arrangement = true)
        f.click(35f, 50f)
        assertEquals("5", f.interaction.selectedId)
    }

    @Test
    fun clickingTheTerrainAwayFromTheLineSelectsTheTerrain() {
        val f = fixture(arrangement = true)
        f.click(35f, 80f)
        assertEquals("1", f.interaction.selectedId)
    }

    @Test
    fun aCopyWinsOverTheLine() {
        val f = fixture(arrangement = true, copy = true)
        f.click(50f, 50f)
        assertEquals("12", f.interaction.selectedId)
    }

    @Test
    fun selectingSomethingElseHidesTheHandles() {
        val f = fixture(arrangement = true)
        f.click(35f, 50f)
        val (x, y) = f.pixelOf(80f, 50f)
        assertTrue(f.queries.pathHandleAt(x, y, W, H) is PathHit.Point)
        f.click(35f, 80f)
        assertNull(f.queries.pathHandleAt(x, y, W, H))
    }
}
