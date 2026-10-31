/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import com.badlogic.gdx.math.Quaternion
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
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
            Vector3(50f, 0f, 30f),
            Quaternion(0f, 0.38268343f, 0f, 0.92387953f),
            Vector3(2f, 1f, 2f),
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

    @Test
    fun aMaskThatDoesNotDivideTheTerrainPaintsUnderTheWorldCircle() {
        val small = 40 // a texel every 100 / 39 units, not a whole number
        val smallCell = size.toFloat() / (small - 1)
        val mask = ByteArray(small * small)
        val from = Vector3(20f, 0f, 60f).mul(tilted)
        val to = Vector3(80f, 0f, 40f).mul(tilted)
        val rect = FoliageBrush(flatTerrain(), small, tilted).stroke(mask, from, to, 12f, 1f, FoliageBrushMode.Paint)
        assertFalse(rect.isEmpty)
        var raised = 0
        for (gz in 0 until small) for (gx in 0 until small) {
            if ((mask[gz * small + gx].toInt() and 0xFF) == 0) continue
            raised++
            val worldTexel = Vector3(gx * smallCell, 0f, gz * smallCell).mul(tilted)
            val distance = distanceToSegment(worldTexel.x, worldTexel.z, from.x, from.z, to.x, to.z)
            assertTrue("texel $gx/$gz raised $distance world units from the path", distance <= 12f + 1e-2f)
            assertTrue("texel $gx/$gz outside the dirty rectangle", gx in rect.minX..rect.maxX && gz in rect.minZ..rect.maxZ)
        }
        assertTrue("no texel was raised", raised > 0)
    }

    private fun sha256(mask: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(mask).joinToString("") { "%02x".format(it) }

    private val tilted = PlacementTransform(
        Vector3(50f, 0f, 30f),
        Quaternion(0f, 0.38268343f, 0f, 0.92387953f),
        Vector3(2f, 1f, 2f),
    ).toMatrix()

    // Golden bytes of the brush as it was before the brush engine was shared: a later refactor must reproduce them.
    @Test
    fun goldenPaintThenEraseOverIdentityEntity() {
        val from = Vector3(10f, 0f, 50f)
        val to = Vector3(90f, 0f, 45f)
        val mask = emptyMask()
        FoliageBrush(flatTerrain(), resolution, Matrix4()).stroke(mask, from, to, 10f, 0.6f, FoliageBrushMode.Paint)
        assertEquals("paint", GOLDEN_PAINT, sha256(mask))
        FoliageBrush(flatTerrain(), resolution, Matrix4()).stroke(mask, from, Vector3(60f, 0f, 55f), 7f, 0.9f, FoliageBrushMode.Erase)
        assertEquals("erase", GOLDEN_ERASE, sha256(mask))
    }

    @Test
    fun goldenPaintOverScaledYawRotatedEntity() {
        val mask = emptyMask()
        val from = Vector3(20f, 0f, 60f).mul(tilted)
        val to = Vector3(80f, 0f, 40f).mul(tilted)
        FoliageBrush(flatTerrain(), resolution, tilted).stroke(mask, from, to, 10f, 1f, FoliageBrushMode.Paint)
        assertEquals("tilted paint", GOLDEN_TILTED, sha256(mask))
    }

    @Test
    fun goldenPaintWithANonDefaultMaskResolution() {
        val small = 40
        val mask = ByteArray(small * small)
        FoliageBrush(flatTerrain(), small, tilted)
            .stroke(mask, Vector3(20f, 0f, 60f).mul(tilted), Vector3(80f, 0f, 40f).mul(tilted), 12f, 0.7f, FoliageBrushMode.Paint)
        assertEquals("small mask paint", GOLDEN_SMALL, sha256(mask))
    }

    @Test
    fun aStrokeSplitIntoManyDragsPaintsTheSameBytesAsOneDrag() {
        val a = Vector3(10f, 0f, 50f)
        val b = Vector3(50f, 0f, 50f)
        val c = Vector3(90f, 0f, 50f)
        val whole = emptyMask()
        FoliageBrush(flatTerrain(), resolution, Matrix4()).stroke(whole, a, c, 10f, 0.5f, FoliageBrushMode.Paint)
        val split = emptyMask()
        val brush = FoliageBrush(flatTerrain(), resolution, Matrix4())
        brush.stroke(split, a, b, 10f, 0.5f, FoliageBrushMode.Paint)
        brush.stroke(split, b, c, 10f, 0.5f, FoliageBrushMode.Paint)
        assertArrayEquals(whole, split)
    }

    /** A delta of exactly 0.5 at the stamp centre: 1/128 of the 64 units. Round half up paints 1, erases 1. */
    @Test
    fun aHalfWayDeltaRoundsUpForPaintAndForErase() {
        val centre = Vector3(40 * cell, 0f, 40 * cell)
        val index = 40 * resolution + 40
        val painted = emptyMask()
        FoliageBrush(flatTerrain(), resolution, Matrix4()).stroke(painted, centre, centre, 5f, 1f / 128f, FoliageBrushMode.Paint)
        assertEquals(1, painted[index].toInt() and 0xFF)

        val erased = emptyMask().also { it[index] = 5 }
        FoliageBrush(flatTerrain(), resolution, Matrix4()).stroke(erased, centre, centre, 5f, 1f / 128f, FoliageBrushMode.Erase)
        assertEquals(4, erased[index].toInt() and 0xFF)
    }

    private companion object {
        const val GOLDEN_PAINT = "d27909cd9c91453e92f38ec483bbdd53c376feb08ba128466625004e6a09c9bf"
        const val GOLDEN_ERASE = "5fae32d7a50dc9486d58318e6b1b2477a1a5b5dac1b1c7942afcc889cd6de535"
        const val GOLDEN_TILTED = "c762c83b948d9ce36f48a3ec60555a608c53476ae7ab3554b02a880f9c05d130"
        const val GOLDEN_SMALL = "9c809f6e41726879fe008864a409840e0bd4269ad92e5dbbaea90d5d8a2c4510"
    }
}
