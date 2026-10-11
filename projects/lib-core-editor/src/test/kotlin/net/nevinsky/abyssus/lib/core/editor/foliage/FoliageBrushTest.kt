/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.Quat
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.sqrt

class FoliageBrushTest {
    private val resolution = 128
    private val size = 100
    private val cell = size.toFloat() / (resolution - 1)

    private fun flatTerrain(): TerrainData = TerrainData(3, FloatArray(9), size, 1f)

    private fun emptyMask(): ByteArray = ByteArray(resolution * resolution)

    /** The position of the centre of texel ([gx], [gz]): terrain-local, or world after `mul(world)`. */
    private fun texel(gx: Int, gz: Int): Vector3 = Vector3(gx * cell, 0f, gz * cell)

    /** The distance in the xz plane from ([px], [pz]) to the segment [ax, az]-[bx, bz]. */
    private fun distanceToSegment(px: Float, pz: Float, ax: Float, az: Float, bx: Float, bz: Float): Float {
        val dx = bx - ax
        val dz = bz - az
        val length = dx * dx + dz * dz
        val along = if (length <= 0f) 0f else ((px - ax) * dx + (pz - az) * dz) / length
        val t = along.coerceIn(0f, 1f)
        val qx = ax + dx * t - px
        val qz = az + dz * t - pz
        return sqrt(qx * qx + qz * qz)
    }

    @Test
    fun strokeRaisesOnlyTexelsWithinRadiusOfThePath() {
        val brush = FoliageBrush(flatTerrain(), resolution, Matrix4())
        val mask = emptyMask()
        val from = Vector3(10f, 0f, 50f)
        val to = Vector3(90f, 0f, 50f)
        val rect = brush.stroke(mask, from, to, 10f, 1f, FoliageBrushMode.Paint)
        assertFalse(rect.isEmpty)
        var raised = 0
        for (gz in 0 until resolution) for (gx in 0 until resolution) {
            val value = mask[gz * resolution + gx].toInt() and 0xFF
            if (value == 0) continue
            raised++
            val p = texel(gx, gz)
            val distance = distanceToSegment(p.x, p.z, from.x, from.z, to.x, to.z)
            assertTrue("texel $gx/$gz raised $distance units from the path", distance <= 10f + 1e-2f)
        }
        assertTrue("no texel was raised", raised > 0)
        // non-vacuous: the middle of the path is painted, the texel nearest (50, 50) is above zero
        val mid = (50f / cell).roundToInt()
        assertTrue((mask[mid * resolution + mid].toInt() and 0xFF) > 0)
    }

    @Test
    fun eraseOnAnEmptyMaskChangesNothing() {
        val brush = FoliageBrush(flatTerrain(), resolution, Matrix4())
        val mask = emptyMask()
        val rect = brush.stroke(mask, Vector3(10f, 0f, 50f), Vector3(90f, 0f, 50f), 10f, 1f, FoliageBrushMode.Erase)
        assertTrue(rect.isEmpty)
        assertArrayEquals(emptyMask(), mask)
    }

    @Test
    fun eraseLowersPaintedValuesAndReportsExactlyTheChangedTexels() {
        val mask = emptyMask()
        val from = Vector3(10f, 0f, 50f)
        val to = Vector3(90f, 0f, 50f)
        FoliageBrush(flatTerrain(), resolution, Matrix4())
            .stroke(mask, from, to, 10f, 1f, FoliageBrushMode.Paint)
        val painted = mask.copyOf()
        val rect = FoliageBrush(flatTerrain(), resolution, Matrix4())
            .stroke(mask, from, to, 10f, 1f, FoliageBrushMode.Erase)
        assertFalse(rect.isEmpty)
        var lowered = 0
        for (gz in 0 until resolution) for (gx in 0 until resolution) {
            val index = gz * resolution + gx
            if ((mask[index].toInt() and 0xFF) < (painted[index].toInt() and 0xFF)) lowered++
            if (painted[index] != mask[index]) {
                assertTrue(
                    "texel $gx/$gz changed outside the dirty rectangle",
                    gx in rect.minX..rect.maxX && gz in rect.minZ..rect.maxZ,
                )
            }
        }
        assertTrue("no texel was lowered", lowered > 0)
    }

    @Test
    fun rotatedScaledEntityPaintsUnderTheWorldCircle() {
        // yaw of 45 degrees, twice the size along x and z, moved off the origin
        val world = PlacementTransform(
            Vec3(50f, 0f, 30f),
            Quat(0f, 0.38268343f, 0f, 0.92387953f),
            Vec3(2f, 1f, 2f),
        ).toMatrix()
        val brush = FoliageBrush(flatTerrain(), resolution, world)
        val mask = emptyMask()
        val from = Vector3(20f, 0f, 60f).mul(world)
        val to = Vector3(80f, 0f, 40f).mul(world)
        val rect = brush.stroke(mask, from, to, 10f, 1f, FoliageBrushMode.Paint)
        assertFalse(rect.isEmpty)
        var raised = 0
        for (gz in 0 until resolution) for (gx in 0 until resolution) {
            val value = mask[gz * resolution + gx].toInt() and 0xFF
            if (value == 0) continue
            raised++
            val worldTexel = texel(gx, gz).mul(world)
            val distance = distanceToSegment(worldTexel.x, worldTexel.z, from.x, from.z, to.x, to.z)
            assertTrue("texel $gx/$gz raised $distance world units from the path", distance <= 10f + 1e-2f)
        }
        assertTrue("no texel was raised", raised > 0)
        // non-vacuous: the middle of the local path is painted, the brush covers a world circle of radius 10
        val mid = (50f / cell).roundToInt()
        assertTrue((mask[mid * resolution + mid].toInt() and 0xFF) > 0)
    }
}
