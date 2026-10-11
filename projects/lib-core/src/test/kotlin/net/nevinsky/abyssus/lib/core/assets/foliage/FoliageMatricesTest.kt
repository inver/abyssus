/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

/** [FoliageMatrices] on a synthetic 10 x 10 plane rising 0.5 per unit along x, with no files and no GL. */
class FoliageMatricesTest {
    private val resolution = 11
    private val terrain = TerrainData(
        resolution,
        FloatArray(resolution * resolution) { (it % resolution) * 0.5f },
        10,
        1f,
    )
    private val matrices = FoliageMatrices()
    private val out = Matrix4()

    private fun copy(x: Float = 4f, z: Float = 3f, yaw: Float = 0f, scale: Float = 1f) =
        FoliageCopy(0, x, z, yaw, scale)

    private fun axis(column: Int) = Vector3(out.values[column * 4], out.values[column * 4 + 1], out.values[column * 4 + 2])

    @Test
    fun alignZeroStaysUprightAndSitsOnTheSurface() {
        checkNotNull(matrices.copy(terrain, 0f, Matrix4(), copy(), out))
        val origin = Vector3().also { out.getTranslation(it) }
        assertEquals(2f, origin.y, 1e-4f)
        assertEquals(0f, axis(1).x, 1e-5f)
        assertEquals(1f, axis(1).y, 1e-5f)
    }

    @Test
    fun alignOneLeansTheUpAxisOntoTheSurfaceNormal() {
        checkNotNull(matrices.copy(terrain, 1f, Matrix4(), copy(), out))
        val normal = Vector3(-0.5f, 1f, 0f).nor()
        val up = axis(1).nor()
        assertEquals(normal.x, up.x, 1e-3f)
        assertEquals(normal.y, up.y, 1e-3f)
        assertEquals(normal.z, up.z, 1e-3f)
    }

    @Test
    fun alignBlendsBetweenUprightAndTheNormal() {
        checkNotNull(matrices.copy(terrain, 0.5f, Matrix4(), copy(), out))
        val up = axis(1).nor()
        val full = Vector3(-0.5f, 1f, 0f).nor()
        assertEquals(true, up.x < 0f && up.x > full.x)
    }

    @Test
    fun yawRotatesAboutTheUpAxisAndScaleIsUniform() {
        checkNotNull(matrices.copy(terrain, 0f, Matrix4(), copy(yaw = (PI / 2).toFloat(), scale = 2f), out))
        assertEquals(2f, axis(0).len(), 1e-4f)
        assertEquals(2f, axis(1).len(), 1e-4f)
        assertEquals(2f, axis(2).len(), 1e-4f)
        // a quarter turn about +Y sends the local +x axis to world -z
        assertEquals(-2f, axis(0).z, 1e-4f)
        assertEquals(0f, axis(0).x, 1e-4f)
    }

    @Test
    fun theEntityTransformIsAppliedFirstIncludingNonUniformScale() {
        val entity = Matrix4().setToTranslation(100f, 5f, -20f).scale(2f, 3f, 4f)
        checkNotNull(matrices.copy(terrain, 0f, entity, copy(), out))
        val origin = Vector3().also { out.getTranslation(it) }
        assertEquals(100f + 2f * 4f, origin.x, 1e-3f)
        assertEquals(5f + 3f * 2f, origin.y, 1e-3f)
        assertEquals(-20f + 4f * 3f, origin.z, 1e-3f)
        assertEquals(2f, axis(0).len(), 1e-4f)
        assertEquals(3f, axis(1).len(), 1e-4f)
        assertEquals(4f, axis(2).len(), 1e-4f)
    }

    @Test
    fun aLocalOffsetIsCarriedByTheFullMatrix() {
        val entity = Matrix4().setToTranslation(10f, 0f, 0f)
        checkNotNull(matrices.copy(terrain, 0f, entity, copy(yaw = (PI / 2).toFloat(), scale = 2f), out))
        val world = Vector3(1f, 0f, 0f).mul(out)
        val origin = Vector3().also { out.getTranslation(it) }
        // local +x is two units long and points to world -z after the quarter turn
        assertEquals(origin.x, world.x, 1e-4f)
        assertEquals(origin.z - 2f, world.z, 1e-4f)
        assertEquals(2f, sqrt((world.x - origin.x) * (world.x - origin.x) + (world.z - origin.z) * (world.z - origin.z)), 1e-4f)
    }

    @Test
    fun aCopyOutsideTheGridLeavesTheOutputAlone() {
        out.setToTranslation(7f, 8f, 9f)
        assertNull(matrices.copy(terrain, 1f, Matrix4(), copy(x = 11f), out))
        assertEquals(7f, out.values[Matrix4.M03], 0f)
    }
}
