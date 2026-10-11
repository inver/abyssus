/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import kotlin.math.sqrt

private const val UNTITLED_TERRAIN = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
private const val FOLIAGE_FIXTURE_LAYER = 0

/**
 * The instance matrices of design decision 5: a copy's transform is the terrain entity's transform, then its position
 * on the surface, then the lean by `alignToNormal`, then yaw and uniform scale, so the instance matrix (written into
 * the instance attributes, `Renderable.worldTransform` staying at the identity) stands where the placement put it.
 */
class FoliageChunkMatricesTest {
    private val project = testProject("Untitled")
    private val files = testFileLoader(project)
    private val terrain =
        checkNotNull(TerrainLoader(files, testMetaLoader(project)).prepare(UNTITLED_TERRAIN)) { "the Untitled terrain" }
            .staged.data
    private val matrices = FoliageMatrices()
    private val out = Matrix4()
    private val origin = Vector3()

    /** The terrain entity of `Main Scene`: a translation, no rotation and no scale. */
    private fun terrainEntity(): Matrix4 {
        val scene = SceneLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files).load("Main Scene.scene")
        val position = checkNotNull(scene.ecs) { "the scene's ecs" }
            .get("1").get("components").get("PositionComponent").get("localPosition")
        fun x(name: String) = position.get(name).asDouble().toFloat()
        return Matrix4().setToTranslation(x("x"), x("y"), x("z"))
    }

    @Test
    fun aCopyOriginSitsOnTheHeightSampleUnderTheEntityTransform() {
        val entity = terrainEntity()
        val copy = FoliageCopy(0, 10f, 20f, 0f, 1f)

        assertNotNull(matrices.copy(terrain, 0f, entity, copy, out))
        out.getTranslation(origin)
        val expected = Vector3(10f, checkNotNull(terrain.heightAt(10f, 20f)) { "inside the terrain" }, 20f).mul(entity)
        assertEquals(expected.x, origin.x, 1e-4f)
        assertEquals(expected.y, origin.y, 1e-4f)
        assertEquals(expected.z, origin.z, 1e-4f)
    }

    @Test
    fun alignZeroKeepsTheCopyUprightUnderTheEntityTransform() {
        checkNotNull(matrices.copy(terrain, 0f, terrainEntity(), FoliageCopy(0, 10f, 20f, 0f, 1f), out))
        val values = out.getValues().copyOf()
        assertEquals(0f, values[4], 1e-4f) // the instance's up axis, column 1 of the matrix
        assertEquals(1f, values[5], 1e-4f)
        assertEquals(0f, values[6], 1e-4f)
    }

    @Test
    fun alignOneTiltsTheUpAxisOntoTheSurfaceNormal() {
        val sloped = TerrainData(2, floatArrayOf(0f, 1f, 0f, 1f), 2, 1f)

        assertNull(matrices.copy(sloped, 0f, Matrix4(), FoliageCopy(0, 50f, 50f, 0f, 1f), out))
        assertNotNull(matrices.copy(sloped, 0f, Matrix4(), FoliageCopy(0, 1f, 1f, 0f, 1f), out))
        val upright = out.getValues().copyOf() // the up axis: align 0 keeps it on +Y, even on the slope
        assertEquals(0f, upright[4], 1e-4f)
        assertEquals(1f, upright[5], 1e-4f)
        assertEquals(0f, upright[6], 1e-4f)

        assertNotNull(matrices.copy(sloped, 1f, Matrix4(), FoliageCopy(0, 1f, 1f, 0f, 1f), out))

        out.getTranslation(origin)
        assertEquals(checkNotNull(sloped.heightAt(1f, 1f)), origin.y, 1e-4f)
        // normalize(-dh/dx, 1, -dh/dz) over the 2x2 patch: dh/dx = 1/2 world unit per unit, dh/dz = 0
        val len = sqrt(0.25f + 1f)
        val values = out.getValues().copyOf()
        assertEquals(-0.5f / len, values[4], 1e-3f)
        assertEquals(1f / len, values[5], 1e-3f)
        assertEquals(0f, values[6], 1e-3f)
    }

    @Test
    fun yawTurnsTheXAxisAndScaleReachesEveryAxis() {
        val flat = TerrainData(2, floatArrayOf(0f, 0f, 0f, 0f), 2, 1f)

        assertNotNull(matrices.copy(flat, 0f, Matrix4(), FoliageCopy(0, 1f, 1f, PI_OVER_TWO, 2f), out))

        val values = out.getValues().copyOf()
        assertEquals(0f, values[0], 1e-4f) // yaw pi/2 turns +X onto -Z
        assertEquals(0f, values[1], 1e-4f)
        assertEquals(-2f, values[2], 1e-4f)
        assertEquals(0f, values[4], 1e-4f)
        assertEquals(2f, values[5], 1e-4f) // the up axis keeps pointing up, twice as long
        assertEquals(0f, values[6], 1e-4f)
    }

    @Test
    fun aCopyOutsideTheTerrainIsNotPlaced() {
        out.setToTranslation(3f, 4f, 5f)
        assertNull(matrices.copy(terrain, 1f, Matrix4(), FoliageCopy(0, 10_000f, 10_000f, 0f, 1f), out))
        val left = out.getTranslation(Vector3())
        assertEquals(Vector3(3f, 4f, 5f), left) // [out] is left alone
    }

    @Test
    fun everyBakedCopyStandsOnTheSurfaceOfTheFoliageFixtureTerrain() {
        val foliage = testProject("Foliage")
        val prepared = checkNotNull(
            FoliageLoader(testFileLoader(foliage), testMetaLoader(foliage), TerrainLoader(testFileLoader(foliage), testMetaLoader(foliage)))
                .prepare("foliage_meadow"),
        ) { "the Foliage fixture" }.staged
        val ground = checkNotNull(prepared.terrain) { "the fixture terrain" }
        val layer = checkNotNull(prepared.bake) { "the fixture bake" }.layers[FOLIAGE_FIXTURE_LAYER]
        assertTrue("the OBJECT layer has copies", layer.copyCount > 0)

        var checked = 0
        for (chunk in layer.chunks) {
            for (copy in chunk) {
                assertNotNull(matrices.copy(ground, 0f, Matrix4(), copy, out))
                out.getTranslation(origin)
                assertEquals(copy.x, origin.x, 1e-4f)
                assertEquals(checkNotNull(ground.heightAt(copy.x, copy.z)) { "a baked copy is inside the terrain" },
                    origin.y, 1e-3f)
                assertEquals(copy.z, origin.z, 1e-4f)
                checked++
            }
        }
        assertEquals(layer.copyCount, checked)
    }
}

private const val PI_OVER_TWO = 1.5707964f
