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
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.water.InvalidWater
import net.nevinsky.abyssus.lib.core.editor.water.ValidWater
import net.nevinsky.abyssus.lib.core.editor.water.WaterSurface
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import org.junit.Assert.assertEquals
import org.junit.Test

private const val W = 800
private const val H = 600

/** Picking water against terrain and models over a hand-built frame, with the real queries and no GL. */
class SceneQueriesWaterTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    /** Looks down at the middle of a 100 x 100 terrain from (50, 60, 110). */
    private fun camera(position: Vector3 = Vector3(50f, 60f, 110f)) = PerspectiveCamera(67f, W.toFloat(), H.toFloat()).also {
        it.position.set(position)
        it.lookAt(50f, 0f, 40f)
        it.near = 0.1f
        it.far = 400f
        it.update()
    }

    private val presets = WaterPresets()
    private fun lake(id: String = "lake", level: Double = 2.0, x: Double = 50.0, z: Double = 50.0) =
        presets.defaults(WaterPreset.LAKE, id, "Lake", x, level, z).copy(width = 40.0, length = 40.0)

    private class Fixture(val camera: PerspectiveCamera, val queries: SnapshotSceneQueries) {
        fun pixelOf(x: Float, y: Float, z: Float): Pair<Int, Int> {
            val v = Vector3(x, y, z)
            camera.project(v, 0f, 0f, W.toFloat(), H.toFloat())
            return v.x.toInt() to (H - v.y.toInt())
        }

        fun pick(x: Float, y: Float, z: Float): SceneTarget? = pixelOf(x, y, z).let { queries.pickTarget(it.first, it.second, W, H) }
    }

    private fun fixture(
        terrainHeight: Float = 0f,
        water: List<WaterSurface> = listOf(ValidWater(lake())),
        boxes: Boolean = false,
        camera: PerspectiveCamera = camera(),
    ): Fixture {
        val heights = FloatArray(9) { terrainHeight }
        val terrain = TerrainTarget("1", TerrainData(3, heights, 100, 1f), Matrix4())
        val content = SceneContent(terrains = listOf(AssetPlacement("1", "terrain", PlacementTransform.IDENTITY)), water = water)
        val tall = if (boxes) listOf(snapshotBoxOf("9", BoundingBox(Vector3(-3f, 0f, -3f), Vector3(3f, 12f, 3f)), Matrix4().setToTranslation(50f, 0f, 50f))) else emptyList()
        val frame = FrameSnapshot(camera, tall, listOf(snapshotTerrainOf(terrain)), 1L)
        return Fixture(camera, SnapshotSceneQueries({ frame }, SceneViewState()) { content })
    }

    @Test fun exposedWaterIsPickedOverTheDeeperTerrain() {
        assertEquals(SceneTarget.Water("lake"), fixture().pick(50f, 2f, 50f))
    }

    @Test fun terrainAboveTheLevelWinsOverTheWaterRectangleBehindIt() {
        // the terrain is higher than the water everywhere, so a ray meets the terrain before the water plane
        assertEquals(SceneTarget.Entity("1"), fixture(terrainHeight = 5f).pick(50f, 5f, 50f))
    }

    @Test fun anIslandPicksTheTerrainAndTheRestOfTheLakePicksWater() {
        val heights = FloatArray(25) { 0f }.also { it[12] = 9f } // 5x5: a peak in the middle
        val terrain = TerrainTarget("1", TerrainData(5, heights, 100, 1f), Matrix4())
        val content = SceneContent(terrains = listOf(AssetPlacement("1", "terrain", PlacementTransform.IDENTITY)), water = listOf(ValidWater(lake(level = 2.0))))
        val frame = FrameSnapshot(camera(), emptyList(), listOf(snapshotTerrainOf(terrain)), 1L)
        val f = Fixture(frame.camera, SnapshotSceneQueries({ frame }, SceneViewState()) { content })
        assertEquals(SceneTarget.Entity("1"), f.pick(50f, 9f, 50f))
        assertEquals(SceneTarget.Water("lake"), f.pick(32f, 2f, 32f))
    }

    @Test fun aNearerModelWinsOverTheWater() {
        assertEquals(SceneTarget.Entity("9"), fixture(boxes = true).pick(50f, 6f, 50f))
    }

    @Test fun disabledAndInvalidSurfacesAreNotPicked() {
        val disabled = ValidWater(lake().copy(enabled = false))
        val invalid = InvalidWater("bad", "Bad", listOf("width"))
        assertEquals(SceneTarget.Entity("1"), fixture(water = listOf(disabled, invalid)).pick(50f, 0f, 50f))
    }

    @Test fun outsideTheRectangleThereIsNoWater() {
        assertEquals(SceneTarget.Entity("1"), fixture().pick(10f, 0f, 90f))
    }

    @Test fun aCameraBelowTheLevelPicksNoWaterThroughItsBackFace() {
        val below = fixture(water = listOf(ValidWater(lake(level = 80.0))))
        // the camera is at y = 60, under a surface at 80: it must not select the water through its back face
        assertEquals(SceneTarget.Entity("1"), below.pickTarget(W / 2, H / 2))
    }

    @Test fun theHigherOfTwoLevelsIsPickedWhereTheyOverlap() {
        val low = ValidWater(lake("low", level = 1.0))
        val high = ValidWater(lake("high", level = 4.0))
        assertEquals(SceneTarget.Water("high"), fixture(water = listOf(low, high)).pick(50f, 4f, 50f))
        assertEquals(SceneTarget.Water("high"), fixture(water = listOf(high, low)).pick(50f, 4f, 50f))
    }

    @Test fun waterIsNeverGroundForDroppingOrArrangements() {
        val f = fixture(terrainHeight = 0f, water = listOf(ValidWater(lake(level = 3.0))))
        val ray = f.queries.rayAt(f.pixelOf(50f, 0f, 50f).first, f.pixelOf(50f, 0f, 50f).second, W, H)!!
        assertEquals(0f, f.queries.groundHit(ray)!!.y, 0.05f)
    }

    @Test fun aModelDropsToTheTerrainNotToWaterAboveIt() {
        val f = fixture(terrainHeight = 0f, water = listOf(ValidWater(lake(level = 3.0))), boxes = true)
        assertEquals(0f, f.queries.groundBelow("9")!!, 1e-4f)
        assertEquals(0f, f.queries.lowestPoint("9")!!, 1e-4f)
    }

    private fun Fixture.pickTarget(x: Int, y: Int): SceneTarget? = queries.pickTarget(x, y, W, H)
}
