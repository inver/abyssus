/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMatrices
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.editor.terrainData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Foliage keeps no heights: a copy of a bake holds its terrain-local x and z and takes its height from the terrain when
 * it is drawn. So after a hill is raised under a bake made before, on the fixture's terrain, the copies sit on the new
 * surface at their old x and z. Headless: the real terrain, the real brush and the real matrices, no files written.
 */
class FoliageFollowsSculptTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private val terrain = terrainData(testProject("Untitled"), "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b")
    private val matrices = FoliageMatrices()

    /** A bake made before the stroke: copies on a grid around the middle of the terrain, x and z only. */
    private val bake = (0 until 9).map { FoliageCopy(0, 600f + (it % 3) * 200f, 600f + (it / 3) * 200f, 0f, 1f) }

    private fun position(copy: FoliageCopy): Vector3 =
        Vector3().also { checkNotNull(matrices.copy(terrain, 0f, Matrix4(), copy, MATRIX)).getTranslation(it) }

    @Test
    fun `copies take the new surface at their previous x and z`() {
        val before = bake.map(::position)
        val size = terrain.size
        val resolution = terrain.resolution
        val draft = SculptDraft(resolution, terrain.heights.copyOf(), terrain.heights.copyOf(), FloatArray(resolution * resolution))
        val centre = Vector3(800f, 0f, 800f)
        val rect = SculptBrush(draft, size, Matrix4()).stroke(centre, centre, 150f, 1f, RaiseLowerTool(rate = 40f), false)
        assertTrue(!rect.isEmpty)
        // the view copies the draft's heights into its own terrain in place; foliage reads that terrain
        terrain.copyHeights(draft.composed, rect.minX, rect.minZ, rect.maxX, rect.maxZ)
        val after = bake.map(::position)
        var raised = 0
        for (i in bake.indices) {
            assertEquals("x is the bake's", bake[i].x, after[i].x, 1e-3f)
            assertEquals("z is the bake's", bake[i].z, after[i].z, 1e-3f)
            assertEquals(terrain.heightAt(bake[i].x, bake[i].z)!!, after[i].y, 1e-3f)
            if (after[i].y > before[i].y + 1f) raised++
        }
        assertTrue("copies under the hill moved up", raised > 0)
        assertNotNull(bake.firstOrNull { position(it).y == before[bake.indexOf(it)].y }) // far copies stay where they were
    }

    private companion object {
        val MATRIX = Matrix4()
    }
}
