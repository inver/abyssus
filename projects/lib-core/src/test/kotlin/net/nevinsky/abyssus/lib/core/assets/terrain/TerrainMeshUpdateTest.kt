/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.terrain

import com.badlogic.gdx.backends.lwjgl3.TestGl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TerrainMeshUpdateTest {
    @Test
    fun theUpdateGrowsTheRectangleByOneVertexAndClampsAtTheEdges() {
        val plan = terrainRegionPlan(100, 40, 50, 45, 52)
        assertEquals(TerrainRegionPlan(39, 49, 46, 53, false), plan)
        assertEquals(TerrainRegionPlan(0, 0, 11, 11, false), terrainRegionPlan(100, 0, 0, 10, 10))
        assertEquals(TerrainRegionPlan(90, 90, 99, 99, false), terrainRegionPlan(100, 91, 91, 99, 99))
    }

    @Test
    fun aLargeRectangleFallsBackToTheFullUpload() {
        assertTrue(terrainRegionPlan(100, 5, 5, 94, 94).full)
        assertTrue(terrainRegionPlan(100, 0, 0, 99, 99).full)
        assertFalse(terrainRegionPlan(100, 5, 5, 40, 40).full)
    }

    @Test
    fun aRectangleOutsideTheTerrainIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { terrainRegionPlan(10, 0, 0, 10, 3) }
        assertThrows(IllegalArgumentException::class.java) { terrainRegionPlan(10, 4, 0, 3, 3) }
    }

    @Test
    fun theUpdatedVertexBufferEqualsAFreshMesh() {
        assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)
        TestGl.run {
            val resolution = 40
            val data = TerrainData(resolution, FloatArray(resolution * resolution) { (it % 7) * 0.5f }, 200, 4f)
            val mesh = TerrainMesh(PreparedTerrain(data, emptyMap()), NO_ASSETS)
            try {
                val edited = data.heights.copyOf().also { for (z in 10..14) for (x in 8..20) it[z * resolution + x] += 6f }
                data.copyHeights(edited, 8, 10, 20, 14)
                mesh.updateRegion(8, 10, 20, 14)
                val fresh = TerrainMesh(PreparedTerrain(TerrainData(resolution, edited, 200, 4f), emptyMap()), NO_ASSETS)
                try {
                    assertArrayEquals(fresh.vertexSnapshot(), mesh.vertexSnapshot(), 0f)
                } finally {
                    fresh.dispose()
                }
                // a rectangle over half the grid takes the full path and gives the same buffer
                data.copyHeights(FloatArray(resolution * resolution) { 3f }, 0, 0, resolution - 1, resolution - 1)
                mesh.updateRegion(0, 0, resolution - 1, resolution - 1)
                val flat = TerrainMesh(PreparedTerrain(TerrainData(resolution, FloatArray(resolution * resolution) { 3f }, 200, 4f), emptyMap()), NO_ASSETS)
                try {
                    assertArrayEquals(flat.vertexSnapshot(), mesh.vertexSnapshot(), 0f)
                } finally {
                    flat.dispose()
                }
            } finally {
                mesh.dispose()
            }
        }
    }

    private companion object {
        val NO_ASSETS = net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets { null }
    }
}
