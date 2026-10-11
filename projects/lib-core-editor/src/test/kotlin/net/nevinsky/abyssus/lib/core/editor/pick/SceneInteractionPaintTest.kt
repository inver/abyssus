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
import net.nevinsky.abyssus.lib.core.editor.content.Quat
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageBrush
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageBrushMode
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

private const val WIDTH = 800
private const val HEIGHT = 600

/** The brush radius every stroke of this test paints with, in world units. */
private const val RADIUS = 10f

/** Paint Foliage gestures over a hand-built frame: a flat terrain, a real brush and no renderer, no GL. */
class SceneInteractionPaintTest {
    private val resolution = 128
    private val size = 100
    private val cell = size.toFloat() / (resolution - 1)
    private val mid = (50f / cell).roundToInt()

    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    /** Above (50, 60, 110) looking at the middle of the terrain, so screen points project onto the terrain surface. */
    private fun camera() = PerspectiveCamera(67f, WIDTH.toFloat(), HEIGHT.toFloat()).also {
        it.position.set(50f, 60f, 110f)
        it.lookAt(50f, 0f, 40f)
        it.near = 0.1f
        it.far = 200f
        it.update()
    }

    private fun flat(): TerrainData = TerrainData(3, FloatArray(9), size, 1f)

    private fun terrainContent(transform: PlacementTransform = PlacementTransform.IDENTITY): SceneContent =
        SceneContent(terrains = listOf(AssetPlacement("1", "terrain_meadow", transform)))

    /** A [FoliagePaint] that paints a real mask with a real brush, remembering the stroke's calls. */
    private class RecordingPaint(private val resolution: Int) : FoliagePaint {
        val begun = mutableListOf<Vector3>()
        val moved = mutableListOf<Vector3>()
        var released = 0
        var cancelled = 0
        val mask = ByteArray(resolution * resolution)
        private var atPress = mask.copyOf()
        private var brush: FoliageBrush? = null
        private var last: Vector3? = null

        override fun pressed(terrain: TerrainTarget, at: Vector3): Boolean {
            begun += Vector3(at)
            atPress = mask.copyOf()
            brush = FoliageBrush(terrain.data, resolution, terrain.world)
            last = Vector3(at)
            return true
        }

        override fun dragged(to: Vector3, erase: Boolean) {
            moved += Vector3(to)
            val mode = if (erase) FoliageBrushMode.Erase else FoliageBrushMode.Paint
            brush!!.stroke(mask, last!!, Vector3(to), RADIUS, 1f, mode)
            last = Vector3(to)
        }

        override fun released() {
            released++
        }

        override fun cancelled() {
            cancelled++
            System.arraycopy(atPress, 0, mask, 0, mask.size)
        }

        fun value(gx: Int, gz: Int): Int = mask[gz * resolution + gx].toInt() and 0xFF

        fun isUnpainted(): Boolean = mask.all { it.toInt() == 0 }
    }

    private class Fixture(
        val state: SceneViewState,
        val queries: SnapshotSceneQueries,
        val interaction: SceneInteraction,
        val orbit: OrbitCamera,
        val paint: RecordingPaint,
        val camera: PerspectiveCamera,
    ) {
        /** The Swing pixel of the world point ([x], 0, [z]). */
        fun pixelOf(x: Float, z: Float): Pair<Int, Int> {
            val v = Vector3(x, 0f, z)
            camera.project(v, 0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat())
            return v.x.toInt() to (HEIGHT - v.y.toInt())
        }
    }

    private fun fixture(
        content: SceneContent = terrainContent(),
        terrains: List<TerrainTarget> = listOf(TerrainTarget("1", flat(), Matrix4())),
    ): Fixture {
        val state = SceneViewState()
        val frame = FrameSnapshot(camera(), emptyList(), terrains.map { snapshotTerrainOf(it) }, 7L)
        val queries = SnapshotSceneQueries({ frame }, state) { content }
        val paint = RecordingPaint(resolution)
        val orbit = OrbitCamera(Vec3(50f, 0f, 50f), 60f, 0f, 0.6f)
        val interaction = SceneInteraction(state, queries, orbit).also {
            it.size = ViewSize(WIDTH, HEIGHT, WIDTH, HEIGHT)
            it.foliagePaint = paint
        }
        return Fixture(state, queries, interaction, orbit, paint, frame.camera)
    }

    /** The paint mode on the drawn terrain "1". */
    private fun Fixture.paintOn(erase: Boolean = false) {
        state.selectedId = "1"
        state.paint = FoliagePaintMode("1", 1, RADIUS, 1f, erase)
    }

    /** Press at ([x0], [y0]) and drag to ([x1], [y1]) in [steps] drag events, like a real drag; the button stays down. */
    private fun drag(f: Fixture, x0: Int, y0: Int, x1: Int, y1: Int, steps: Int = 4) {
        f.interaction.pressed(x0, y0, true)
        for (step in 1..steps) {
            f.interaction.dragged(x0 + (x1 - x0) * step / steps, y0 + (y1 - y0) * step / steps, true)
        }
    }

    /** A released paint drag along the path (10, 50) to (90, 50) on the flat terrain. */
    private fun paintBand(f: Fixture) {
        val (x0, y0) = f.pixelOf(10f, 50f)
        val (x1, y1) = f.pixelOf(90f, 50f)
        drag(f, x0, y0, x1, y1)
        f.interaction.released(x1, y1, true)
    }

    /** The distance in the xz plane from ([px], [pz]) to the segment [ax, az]-[bx, bz]. */
    private fun distanceToSegment(px: Float, pz: Float, ax: Float, az: Float, bx: Float, bz: Float): Float {
        val dx = bx - ax
        val dz = bz - az
        val length = dx * dx + dz * dz
        val along = if (length <= 0f) 0f else ((px - ax) * dx + (pz - az) * dz) / length
        val t = along.coerceIn(0f, 1f)
        val qx = ax + dx * t - px
        val qz = az + dz * t - pz
        return kotlin.math.sqrt(qx * qx + qz * qz)
    }

    @Test
    fun enteringPaintModeHidesTheGizmo() {
        val f = fixture()
        f.state.selectedId = "1"
        assertNotNull("the terrain shows its gizmo", f.queries.gizmoHandles(HEIGHT))
        f.state.paint = FoliagePaintMode("1", 1, RADIUS, 1f)
        assertNull(f.queries.gizmoHandles(HEIGHT))
        f.state.paint = null
        assertNotNull(f.queries.gizmoHandles(HEIGHT))
    }

    @Test
    fun aPaintModeOnAnotherEntityDoesNotPaint() {
        val f = fixture()
        f.state.selectedId = "1"
        f.state.paint = FoliagePaintMode("missing", 1, RADIUS, 1f)
        val (x, y) = f.pixelOf(50f, 50f)
        f.interaction.pressed(x, y, true)
        f.interaction.dragged(x + 20, y, true)
        f.interaction.released(x + 20, y, true)
        assertTrue(f.paint.begun.isEmpty())
        assertEquals(0, f.paint.released)
        assertTrue(f.paint.isUnpainted())
    }

    @Test
    fun selectingAnotherEntityLeavesTheMode() {
        // A second terrain beyond the first, at z -100: a click over it selects it and ends the mode.
        val beyond = Matrix4().setToTranslation(0f, 0f, -size.toFloat())
        val content = SceneContent(
            terrains = listOf(
                AssetPlacement("1", "terrain_meadow", PlacementTransform.IDENTITY),
                AssetPlacement(
                    "2",
                    "terrain_meadow",
                    PlacementTransform(Vec3(0f, 0f, -size.toFloat()), Quat.IDENTITY, Vec3(1f, 1f, 1f)),
                ),
            ),
        )
        val f = fixture(
            content = content,
            terrains = listOf(TerrainTarget("1", flat(), Matrix4()), TerrainTarget("2", flat(), beyond)),
        )
        f.paintOn()
        val (x, y) = f.pixelOf(50f, -50f)
        f.interaction.pressed(x, y, true)
        assertTrue("the press is off the painted terrain", f.paint.begun.isEmpty())
        f.interaction.released(x, y, true)
        assertEquals("2", f.state.selectedId)
        assertNull(f.state.paint)
    }

    @Test
    fun leftPressAndDragPaintABandAlongTheDrag() {
        val f = fixture()
        f.paintOn()
        val (x0, y0) = f.pixelOf(10f, 50f)
        val (x1, y1) = f.pixelOf(90f, 50f)
        drag(f, x0, y0, x1, y1)
        f.interaction.released(x1, y1, true)
        assertEquals(1, f.paint.begun.size)
        assertEquals(4, f.paint.moved.size)
        assertEquals(1, f.paint.released)
        assertTrue("the press point arrives in world space", f.paint.begun.single().dst(Vector3(10f, 0f, 50f)) < 0.5f)
        assertTrue("the middle of the drag is painted", f.paint.value(mid, mid) > 0)
        assertEquals("far from the drag", 0, f.paint.value(2, 2))
        assertEquals("30 units beside the drag", 0, f.paint.value(mid, 30))
    }

    @Test
    fun holdingShiftErasesWhilePaintIsChosen() {
        val f = fixture()
        f.paintOn()
        paintBand(f)
        val before = f.paint.value(mid, mid)
        assertTrue("the band is painted", before > 0)
        f.paintOn(erase = true)
        paintBand(f)
        assertTrue("the shift drag lowers the mask", f.paint.value(mid, mid) < before)
    }

    @Test
    fun rightDragsAndTheWheelNavigateWithoutPainting() {
        val f = fixture()
        f.paintOn()
        val target = Vec3(f.orbit.target.x, f.orbit.target.y, f.orbit.target.z)
        val distance = f.orbit.distance
        val (x, y) = f.pixelOf(50f, 50f)
        f.interaction.pressed(x, y, false)
        f.interaction.dragged(x + 40, y + 10, false)
        f.interaction.released(x + 40, y + 10, false)
        assertTrue("a right press is no stroke", f.paint.begun.isEmpty())
        assertNotEquals(target.x, f.orbit.target.x, 1e-6f)
        f.interaction.wheel(-3f)
        assertNotEquals(distance, f.orbit.distance, 1e-6f)
        assertTrue(f.paint.isUnpainted())
    }

    @Test
    fun escapeDiscardsTheStroke() {
        val f = fixture()
        f.paintOn()
        val (x0, y0) = f.pixelOf(20f, 50f)
        val (x1, y1) = f.pixelOf(60f, 50f)
        f.interaction.pressed(x0, y0, true)
        f.interaction.dragged(x1, y1, true)
        assertTrue("the band is painted before the escape", f.paint.value(mid, mid) > 0)
        f.interaction.escape()
        assertEquals(1, f.paint.cancelled)
        assertTrue("the mask is back as it was at the press", f.paint.isUnpainted())
        // the rest of the gesture does nothing
        f.interaction.dragged(x1 + 30, y1, true)
        f.interaction.released(x1 + 30, y1, true)
        assertEquals(0, f.paint.released)
        assertTrue(f.paint.isUnpainted())
    }

    @Test
    fun aPressOffTheTerrainDoesNotPaint() {
        val f = fixture()
        f.paintOn()
        // beside the terrain: the ray through this point misses the terrain square and paints nothing
        val (x, y) = f.pixelOf(50f, -40f)
        f.interaction.pressed(x, y, true)
        f.interaction.dragged(x + 20, y, true)
        f.interaction.released(x + 20, y, true)
        assertTrue(f.paint.begun.isEmpty())
        assertEquals(0, f.paint.released)
        assertTrue(f.paint.isUnpainted())
    }

    @Test
    fun aRotatedScaledTerrainPaintsUnderTheWorldCircle() {
        // yaw of 30 degrees, twice the size along x and z, moved off the origin
        val world = Matrix4().setToTranslation(10f, 0f, 30f).rotate(Vector3.Y, 30f).scale(2f, 1f, 2f)
        val transform = PlacementTransform(
            Vec3(10f, 0f, 30f),
            Quat(0f, 0.25881905f, 0f, 0.96592583f),
            Vec3(2f, 1f, 2f),
        )
        val f = fixture(
            content = terrainContent(transform),
            terrains = listOf(TerrainTarget("1", flat(), world)),
        )
        f.paintOn()
        val from = Vector3(40f, 0f, 70f)
        val to = Vector3(80f, 0f, 55f)
        val (x0, y0) = f.pixelOf(from.x, from.z)
        val (x1, y1) = f.pixelOf(to.x, to.z)
        drag(f, x0, y0, x1, y1)
        f.interaction.released(x1, y1, true)
        // the drag is stamped along the hits the rays actually found, which screen-stepped drags bow slightly
        val path = f.paint.begun + f.paint.moved
        var raised = 0
        for (gz in 0 until resolution) for (gx in 0 until resolution) {
            if (f.paint.value(gx, gz) == 0) continue
            raised++
            val worldTexel = Vector3(gx * cell, 0f, gz * cell).mul(world)
            val distance = path.zipWithNext().minOfOrNull { (a, b) ->
                distanceToSegment(worldTexel.x, worldTexel.z, a.x, a.z, b.x, b.z)
            } ?: 0f
            assertTrue("texel $gx/$gz raised $distance world units from the path", distance <= RADIUS + 0.3f)
        }
        assertTrue("no texel was raised", raised > 0)
        val midWorld = Vector3((from.x + to.x) / 2f, 0f, (from.z + to.z) / 2f).mul(Matrix4(world).inv())
        assertTrue(f.paint.value((midWorld.x / cell).roundToInt(), (midWorld.z / cell).roundToInt()) > 0)
    }

    @Test
    fun anEmptyViewDoesNotPaint() {
        val f = fixture()
        f.paintOn()
        f.interaction.size = ViewSize(0, 0, 0, 0)
        f.interaction.pressed(10, 10, true)
        assertTrue(f.paint.begun.isEmpty())
        assertTrue(f.paint.isUnpainted())
        assertArrayEquals(ByteArray(resolution * resolution), f.paint.mask)
    }
}
