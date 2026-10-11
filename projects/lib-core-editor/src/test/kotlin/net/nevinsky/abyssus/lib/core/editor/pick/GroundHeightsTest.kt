/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.testProject
import net.nevinsky.abyssus.lib.core.editor.terrainData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroundHeightsTest {
    /** 5x5 heights over 100 units, rising with x and z so a wrong lookup shows. */
    private val data = TerrainData(5, FloatArray(25) { (it % 5) * 2f + (it / 5) * 1f }, 100, 1f)

    @Test
    fun theHeightMatchesTheTerrainAtItsLocalPoint() {
        val heights = GroundHeights(listOf(TerrainTarget("1", data, Matrix4())))
        assertEquals(data.heightAt(20f, 0f)!!, heights.heightAt(20f, 0f, -99f), 1e-3f)
        assertEquals(data.heightAt(37.5f, 61f)!!, heights.heightAt(37.5f, 61f, -99f), 1e-3f)
    }

    @Test
    fun theHeightOnTheFixtureTerrainMatchesItsLocalPoint() {
        val terrain = terrainData(testProject("Untitled"), "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b")
        // entity 1 of Main Scene stands at (-38.25267, 0, -32.7754)
        val heights = GroundHeights(listOf(TerrainTarget("1", terrain, Matrix4().setToTranslation(-38.25267f, 0f, -32.7754f))))
        assertEquals(terrain.heightAt(20f + 38.25267f, 32.7754f)!!, heights.heightAt(20f, 0f, -99f), 1e-3f)
    }

    @Test
    fun aTranslatedTerrainIsReadAtTheShiftedPoint() {
        val world = Matrix4().setToTranslation(30f, 5f, -10f)
        val heights = GroundHeights(listOf(TerrainTarget("1", data, world)))
        assertEquals(data.heightAt(20f, 40f)!! + 5f, heights.heightAt(50f, 30f, -99f), 1e-3f)
    }

    @Test
    fun aRotatedTerrainIsReadAtTheTurnedPoint() {
        val world = Matrix4().setToRotation(Vector3.Y, 90f)
        val heights = GroundHeights(listOf(TerrainTarget("1", data, world)))
        // a quarter turn about Y sends local (x, z) to world (z, -x)
        assertEquals(data.heightAt(20f, 40f)!!, heights.heightAt(40f, -20f, -99f), 1e-3f)
    }

    @Test
    fun theHighestTerrainWins() {
        val flatLow = TerrainData(2, FloatArray(4), 100, 1f)
        val flatHigh = TerrainData(2, FloatArray(4) { 7f }, 100, 1f)
        val heights = GroundHeights(listOf(TerrainTarget("a", flatHigh, Matrix4()), TerrainTarget("b", flatLow, Matrix4())))
        assertEquals(7f, heights.heightAt(50f, 50f, -99f), 1e-3f)
    }

    @Test
    fun offTheTerrainTheLineHeightIsUsed() {
        val heights = GroundHeights(listOf(TerrainTarget("1", data, Matrix4())))
        assertNull(heights.terrainHeight(500f, 500f))
        assertEquals(3f, heights.heightAt(500f, 500f, 3f), 0f)
        assertEquals(3f, GroundHeights(emptyList()).heightAt(1f, 1f, 3f), 0f)
    }
}
