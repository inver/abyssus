/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.water.ValidWater
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val W = 800
private const val H = 600

/** Selecting, moving and cancelling water over a hand-built frame; the real interaction and queries, no renderer and no GL. */
class WaterInteractionTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private val lake = WaterPresets().defaults(WaterPreset.LAKE, "lake", "Lake", 50.0, 2.0, 50.0).copy(width = 40.0, length = 40.0)

    private class Fixture(val state: SceneViewState, val queries: SnapshotSceneQueries, val interaction: SceneInteraction, val camera: PerspectiveCamera) {
        val picked = mutableListOf<String>()
        val moves = mutableListOf<Pair<String, Vector3>>()
        var accept = true
        var content: SceneContent = SceneContent()

        fun pixelOf(p: Vector3): Pair<Int, Int> {
            val v = Vector3(p.x, p.y, p.z)
            camera.project(v, 0f, 0f, W.toFloat(), H.toFloat())
            return v.x.toInt() to (H - v.y.toInt())
        }

        fun click(p: Vector3) = pixelOf(p).let { interaction.pressed(it.first, it.second, true); interaction.released(it.first, it.second, true) }

        fun tip(axis: GizmoAxis): Pair<Int, Int> = pixelOf(queries.gizmoHandles(H)!!.tip(axis))

        /** Presses on the [axis] handle and drags by ([dx], [dy]) pixels without releasing. */
        fun dragHandle(axis: GizmoAxis, dx: Int, dy: Int) {
            val (x, y) = tip(axis)
            interaction.pressed(x, y, true)
            interaction.dragged(x + dx, y + dy, true)
        }

        fun release(axis: GizmoAxis, dx: Int, dy: Int) {
            val (x, y) = tip(axis)
            interaction.released(x + dx, y + dy, true)
        }
    }

    private fun fixture(water: List<ValidWater> = listOf(ValidWater(lake)), selected: String? = "lake"): Fixture {
        val camera = PerspectiveCamera(67f, W.toFloat(), H.toFloat()).also {
            it.position.set(50f, 60f, 110f)
            it.lookAt(50f, 0f, 40f)
            it.near = 0.1f
            it.far = 400f
            it.update()
        }
        val state = SceneViewState().also { s -> selected?.let { s.selectedWater = it } }
        val terrain = TerrainTarget("1", TerrainData(3, FloatArray(9), 100, 1f), Matrix4())
        val frame = FrameSnapshot(camera, emptyList(), listOf(snapshotTerrainOf(terrain)), 1L)
        val content = SceneContent(terrains = listOf(AssetPlacement("1", "terrain", PlacementTransform.IDENTITY)), water = water)
        val queries = SnapshotSceneQueries({ frame }, state) { content }
        val interaction = SceneInteraction(state, queries, OrbitCamera(Vector3(50f, 0f, 50f), 60f, 0f, 0.6f)).also { it.size = ViewSize(W, H, W, H) }
        return Fixture(state, queries, interaction, camera).also { f ->
            f.content = content
            interaction.onPickWater = { f.picked += it }
            interaction.onWaterMove = { id, at -> f.moves += id to at; f.accept }
        }
    }

    private fun paramsOf(content: SceneContent) = SceneRenderParams(Color(0f, 0f, 0f, 1f), null, null, CameraParams.DEFAULT, content)

    @Test fun clickingExposedWaterSelectsItAndReportsThePick() {
        val f = fixture(selected = null)
        f.click(Vector3(50f, 2f, 50f))
        assertEquals("lake", f.interaction.selectedWater)
        assertNull(f.interaction.selectedId)
        assertEquals(listOf("lake"), f.picked)
    }

    @Test fun clickingOutsideTheWaterOrOnTerrainSelectsNothing() {
        val f = fixture(selected = null)
        f.click(Vector3(5f, 0f, 90f))
        assertNull(f.interaction.selectedWater)
        assertEquals(SceneTarget.Entity("1"), f.state.selection ?: SceneTarget.Entity("1"))
    }

    @Test fun selectingAnEntityLeavesNoWaterSelected() {
        val f = fixture()
        f.state.selectedId = "7"
        assertNull(f.state.selectedWater)
        assertEquals("7", f.state.selectedId)
        f.state.selectedWater = "lake"
        assertNull("the ids are separate namespaces", f.state.selectedId)
    }

    @Test fun theMoveGizmoSitsOnTheSurfaceCentre() {
        val f = fixture()
        val handles = f.queries.gizmoHandles(H)!!
        assertEquals(Vector3(50f, 2f, 50f), handles.origin)
        assertEquals(GizmoMode.MOVE, handles.mode)
    }

    @Test fun rotationAndSelectModeOfferNoWaterHandles() {
        val f = fixture()
        f.interaction.mode = GizmoMode.ROTATE
        assertNull(f.queries.gizmoHandles(H))
        f.interaction.mode = GizmoMode.SELECT
        assertNull(f.queries.gizmoHandles(H))
        f.interaction.mode = GizmoMode.MOVE
        assertNotNull(f.queries.gizmoHandles(H))
    }

    @Test fun dropIsUnavailableForWater() {
        val f = fixture()
        assertFalse(f.interaction.canDrop)
        f.interaction.drop()
        assertTrue(f.moves.isEmpty())
        assertTrue(f.state.waterPreview.isEmpty())
    }

    @Test fun draggingXPreviewsThenWritesOnlyTheNewCentre() {
        val f = fixture()
        f.dragHandle(GizmoAxis.X, 60, 0)
        assertTrue(f.interaction.isDragging)
        val preview = f.state.waterPreview.getValue("lake")
        assertTrue("moved along X", preview.x > 50f)
        assertEquals(2f, preview.y, 1e-3f)
        assertEquals(50f, preview.z, 1e-3f)
        assertEquals("the saved surface is untouched until release", 50.0, (f.content.water.single() as ValidWater).settings.x, 0.0)
        assertTrue("the shown content follows the preview", (f.queries.content.water.single() as ValidWater).settings.x > 50.0)
        f.release(GizmoAxis.X, 60, 0)
        assertEquals(1, f.moves.size)
        assertEquals("lake", f.moves[0].first)
        assertEquals(preview.x, f.moves[0].second.x, 0.5f)
    }

    @Test fun draggingYChangesTheWaterLevelOnly() {
        val f = fixture()
        f.dragHandle(GizmoAxis.Y, 0, -40)
        val at = f.state.waterPreview.getValue("lake")
        assertTrue("raised", at.y > 2f)
        assertEquals(50f, at.x, 1e-3f)
        assertEquals(50f, at.z, 1e-3f)
        f.release(GizmoAxis.Y, 0, -40)
        assertEquals(at.y, f.moves.single().second.y, 0.5f)
    }

    @Test fun draggingZChangesTheDepthPositionOnly() {
        val f = fixture()
        f.dragHandle(GizmoAxis.Z, 0, 40)
        val at = f.state.waterPreview.getValue("lake")
        assertTrue(at.z != 50f)
        assertEquals(50f, at.x, 1e-3f)
        assertEquals(2f, at.y, 1e-3f)
        f.release(GizmoAxis.Z, 0, 40)
        assertEquals(1, f.moves.size)
    }

    @Test fun escapeRestoresTheOriginalPositionAndWritesNothing() {
        val f = fixture()
        f.dragHandle(GizmoAxis.X, 60, 0)
        assertTrue(f.state.waterPreview.isNotEmpty())
        f.interaction.escape()
        assertTrue(f.state.waterPreview.isEmpty())
        assertFalse(f.interaction.isDragging)
        f.release(GizmoAxis.X, 60, 0)
        assertTrue(f.moves.isEmpty())
        assertEquals("the surface stays selected", "lake", f.interaction.selectedWater)
    }

    @Test fun aPressAndReleaseWithoutMovingWritesNothing() {
        val f = fixture()
        val (x, y) = f.tip(GizmoAxis.X)
        f.interaction.pressed(x, y, true)
        f.interaction.released(x, y, true)
        assertTrue(f.moves.isEmpty())
        assertTrue(f.state.waterPreview.isEmpty())
    }

    @Test fun aRefusedWriteDropsThePreview() {
        val f = fixture().also { it.accept = false }
        f.dragHandle(GizmoAxis.X, 60, 0)
        f.release(GizmoAxis.X, 60, 0)
        assertEquals(1, f.moves.size)
        assertTrue(f.state.waterPreview.isEmpty())
    }

    @Test fun anAcceptedMoveKeepsThePreviewUntilTheSceneIsRead() {
        val f = fixture()
        f.dragHandle(GizmoAxis.X, 60, 0)
        f.release(GizmoAxis.X, 60, 0)
        assertTrue(f.state.waterPreview.isNotEmpty())
        f.interaction.paramsChanged(paramsOf(f.content))
        assertTrue(f.state.waterPreview.isEmpty())
        assertEquals("lake", f.interaction.selectedWater)
    }

    @Test fun aSurfaceDeletedElsewhereClearsTheSelectionAndCancelsItsDrag() {
        val f = fixture()
        val (x, y) = f.tip(GizmoAxis.X)
        f.dragHandle(GizmoAxis.X, 60, 0)
        f.interaction.paramsChanged(paramsOf(SceneContent()))
        assertNull(f.interaction.selectedWater)
        assertNull(f.state.selection)
        f.interaction.released(x + 60, y, true)
        assertTrue("a cancelled drag writes nothing", f.moves.isEmpty())
    }

    @Test fun selectingFromTheTreeSelectsTheSurface() {
        val f = fixture(selected = null)
        f.interaction.selectWater("lake")
        assertEquals("lake", f.interaction.selectedWater)
        assertNotNull(f.queries.gizmoHandles(H))
    }

    @Test fun deleteKeyRemovesTheSelectedSurfaceThroughTheCallback() {
        val f = fixture(selected = null)
        val removed = mutableListOf<String>()
        f.interaction.onWaterDelete = { removed += it; true }
        f.interaction.selectWater("lake")
        f.interaction.backspace()
        assertEquals(listOf("lake"), removed)
    }

    @Test fun snappingRoundsTheDraggedCoordinate() {
        val f = fixture()
        f.state.snap = GizmoSnap(5f, 15f)
        f.dragHandle(GizmoAxis.X, 37, 0)
        assertEquals(0f, f.state.waterPreview.getValue("lake").x % 5f, 1e-3f)
    }
}
