/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val UNTITLED_TERRAIN = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
private const val DRAW_DISTANCE = 80f

/**
 * The culling of design decision 5: a chunk is drawn when its box meets the camera's frustum, and a DETAIL layer
 * additionally draws only within its draw distance; an OBJECT layer draws out to the far plane. The camera stands at
 * (10, 5, 10) looking down +X over the flat `Untitled` terrain, whose 1600 units are cut into 50 by 50 chunks.
 */
class FoliageCullingTest {
    init {
        GdxNativesLoader.load() // `Frustum.update` projects through the native `Matrix4.prj`
    }

    private val project = testProject("Untitled")
    private val terrain =
        checkNotNull(TerrainLoader(testFileLoader(project), testMetaLoader(project)).prepare(UNTITLED_TERRAIN)) {
            "the Untitled terrain"
        }.staged.data
    private val grid = foliageChunkGrid(
        FoliageLayerBake(0, 1, 50, 50, List(50 * 50) { emptyList() }),
        foliageChunkSize(terrain.size),
        terrain,
    )
    private val camera = PerspectiveCamera(60f, 4f, 3f).apply { // a 4 by 3 viewport: aspect 4/3
        position.set(10f, 5f, 10f)
        direction.set(1f, 0f, 0f)
        near = 0.1f
        far = 5000f
        update()
    }

    private fun visible(kind: FoliageLayerKind): BooleanArray =
        visibleFoliageChunks(grid, Matrix4(), kind, DRAW_DISTANCE, camera, BooleanArray(grid.boxes.size))

    @Test
    fun aChunkWithinTheDrawDistanceIsKeptForBothKinds() {
        val detail = visible(FoliageLayerKind.DETAIL)
        val obj = visible(FoliageLayerKind.OBJECT)
        val here = grid.index(2, 0) // x in [64, 96], ahead of the camera and about 54 units away

        assertTrue(detail[here])
        assertTrue(obj[here])
    }

    @Test
    fun aDetailChunkBeyondTheDrawDistanceIsDroppedWhileObjectKeepsIt() {
        val detail = visible(FoliageLayerKind.DETAIL)
        val obj = visible(FoliageLayerKind.OBJECT)
        val near = grid.index(3, 0) // x in [96, 128]: about 86 from the eye
        val horizon = grid.index(49, 0) // x in [1568, 1600]: the far edge, still inside the frustum

        assertFalse(detail[near])
        assertTrue(obj[near])
        assertFalse(detail[horizon])
        assertTrue(obj[horizon])
    }

    @Test
    fun aChunkBehindTheCameraIsDroppedForBothKinds() {
        val behind = grid.index(0, 49) // z in [1568, 1600]: to the camera's side, out of the frustum

        assertFalse(visible(FoliageLayerKind.DETAIL)[behind])
        assertFalse(visible(FoliageLayerKind.OBJECT)[behind])
    }
}
