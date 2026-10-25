/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val W = 800
private const val H = 600

/** Sculpt Terrain gestures over a hand-built frame: a flat terrain, a recording stroke and no renderer, no GL. */
class SceneInteractionSculptTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private val camera = PerspectiveCamera(67f, W.toFloat(), H.toFloat()).also {
        it.position.set(50f, 60f, 110f)
        it.lookAt(50f, 0f, 40f)
        it.near = 0.1f
        it.far = 200f
        it.update()
    }

    private class RecordingSculpt(var accept: Boolean = true) : TerrainSculpt {
        val pressed = mutableListOf<Vector3>()
        val dragged = mutableListOf<Pair<Vector3, Boolean>>()
        var released = 0
        var cancelled = 0
        override fun pressed(terrain: TerrainTarget, at: Vector3): Boolean = accept.also { if (it) pressed += Vector3(at) }
        override fun dragged(to: Vector3, inverted: Boolean) { dragged += Vector3(to) to inverted }
        override fun released() { released++ }
        override fun cancelled() { cancelled++ }
    }

    private val state = SceneViewState()
    private val sculpt = RecordingSculpt()
    private val content = SceneContent(
        terrains = listOf(AssetPlacement("1", "terrain_meadow", PlacementTransform.IDENTITY)),
    )
    private val orbit = OrbitCamera(Vector3(50f, 0f, 50f), 60f, 0f, 0.6f)
    private val interaction: SceneInteraction = run {
        val frame = FrameSnapshot(camera, emptyList(), listOf(snapshotTerrainOf(TerrainTarget("1", TerrainData(3, FloatArray(9), 100, 1f), Matrix4()))), 7L)
        val queries = SnapshotSceneQueries({ frame }, state) { content }
        SceneInteraction(state, queries, orbit).also {
            it.size = ViewSize(W, H, W, H)
            it.terrainSculpt = sculpt
        }
    }

    private fun pixelOf(x: Float, z: Float): Pair<Int, Int> {
        val v = Vector3(x, 0f, z)
        camera.project(v, 0f, 0f, W.toFloat(), H.toFloat())
        return v.x.toInt() to (H - v.y.toInt())
    }

    private fun sculptOn(inverted: Boolean = false) {
        state.selectedId = "1"
        state.sculpt = SculptMode("1", 10f, 1f, inverted)
    }

    @Test
    fun `a press drag and release on the terrain is one stroke`() {
        sculptOn()
        val (x0, y0) = pixelOf(40f, 50f)
        val (x1, y1) = pixelOf(60f, 50f)
        interaction.pressed(x0, y0, true)
        interaction.dragged((x0 + x1) / 2, (y0 + y1) / 2, true)
        interaction.dragged(x1, y1, true)
        interaction.released(x1, y1, true)
        assertEquals(1, sculpt.pressed.size)
        assertEquals(40f, sculpt.pressed[0].x, 1f)
        assertEquals(2, sculpt.dragged.size)
        assertEquals(1, sculpt.released)
        assertEquals(0, sculpt.cancelled)
    }

    @Test
    fun `the left button sculpts instead of orbiting`() {
        sculptOn()
        val before = Triple(orbit.yaw, orbit.pitch, orbit.distance)
        val (x, y) = pixelOf(50f, 50f)
        interaction.pressed(x, y, true)
        interaction.dragged(x + 30, y + 10, true)
        interaction.released(x + 30, y + 10, true)
        assertEquals(before, Triple(orbit.yaw, orbit.pitch, orbit.distance))
    }

    @Test
    fun `the inverted flag of the mode reaches the stroke`() {
        sculptOn(inverted = true)
        val (x, y) = pixelOf(50f, 50f)
        interaction.pressed(x, y, true)
        interaction.dragged(x + 10, y, true)
        assertTrue(sculpt.dragged.single().second)
        interaction.released(x + 10, y, true)
    }

    @Test
    fun `a refused press starts no stroke`() {
        sculptOn()
        sculpt.accept = false
        val (x, y) = pixelOf(50f, 50f)
        interaction.pressed(x, y, true)
        interaction.dragged(x + 10, y, true)
        interaction.released(x + 10, y, true)
        assertTrue(sculpt.dragged.isEmpty())
        assertEquals(0, sculpt.released)
    }

    @Test
    fun `a press in the sky sculpts nothing`() {
        sculptOn()
        interaction.pressed(W / 2, 2, true)
        assertTrue(sculpt.pressed.isEmpty())
    }

    @Test
    fun `escape discards the stroke and writes nothing`() {
        sculptOn()
        val (x, y) = pixelOf(50f, 50f)
        interaction.pressed(x, y, true)
        interaction.escape()
        interaction.released(x, y, true)
        assertEquals(1, sculpt.cancelled)
        assertEquals(0, sculpt.released)
    }

    @Test
    fun `the terrain leaving the scene ends the mode and cancels the stroke`() {
        sculptOn()
        val (x, y) = pixelOf(50f, 50f)
        interaction.pressed(x, y, true)
        interaction.paramsChanged(SceneRenderParams(Color(0f, 0f, 0f, 1f), null, null, CameraParams.DEFAULT, SceneContent()))
        assertNull(state.sculpt)
        assertEquals(1, sculpt.cancelled)
    }

    @Test
    fun `sculpting hides the gizmo and brushing is on`() {
        sculptOn()
        assertTrue(state.brushing)
        assertNull(gizmoHandlesFor(content, camera, state, H))
        state.endBrush()
        assertFalse(state.brushing)
    }
}
