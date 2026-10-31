/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.pick.LineSink
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BrushCursorTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private class Recording : LineSink {
        val segments = mutableListOf<Pair<Vector3, Vector3>>()
        override fun line(from: Vector3, to: Vector3, color: Color) { segments += Vector3(from) to Vector3(to) }
    }

    private val bumpy = TerrainData(3, floatArrayOf(0f, 4f, 1f, 2f, 7f, 3f, 5f, 0f, 6f), 100, 1f)

    /** The circle as the foliage-named cursor drew it before the brush path was shared: its own scale and inverse. */
    private fun reference(terrain: TerrainTarget, at: Vector3, radius: Float): List<Pair<Vector3, Vector3>> {
        val sx = Vector3(1f, 0f, 0f).rot(terrain.world).len()
        val sz = Vector3(0f, 0f, 1f).rot(terrain.world).len()
        val centre = Vector3(at).mul(Matrix4(terrain.world).inv())
        fun point(i: Int): Vector3? {
            val angle = i * Math.PI * 2.0 / 64
            val x = centre.x + radius * cos(angle).toFloat() / sx
            val z = centre.z + radius * sin(angle).toFloat() / sz
            val y = terrain.data.heightAt(x, z) ?: return null
            val p = Vector3(x, y + 0.03f, z).mul(terrain.world)
            return Vector3(p.x, p.y, p.z)
        }
        val out = mutableListOf<Pair<Vector3, Vector3>>()
        var previous = point(0)
        for (i in 1..64) {
            val next = point(i)
            if (previous != null && next != null) out += previous to next
            previous = next
        }
        return out
    }

    private fun assertSameCircle(terrain: TerrainTarget, at: Vector3, radius: Float) {
        val lines = Recording()
        BrushCursor(terrain, at, radius).draw(lines)
        val expected = reference(terrain, at, radius)
        assertTrue("no segment was drawn", expected.isNotEmpty())
        assertEquals(expected.size, lines.segments.size)
        for ((i, pair) in expected.withIndex()) {
            assertEquals("from $i", 0f, pair.first.dst(lines.segments[i].first), 0f)
            assertEquals("to $i", 0f, pair.second.dst(lines.segments[i].second), 0f)
        }
    }

    @Test
    fun theCircleMatchesTheOldOneOnAScaledYawRotatedEntity() {
        val world = PlacementTransform(
            Vector3(50f, 3f, 30f), Quaternion(0f, 0.38268343f, 0f, 0.92387953f), Vector3(2f, 1f, 3f),
        ).toMatrix()
        val at = Vector3(30f, 0f, 20f).mul(world)
        assertSameCircle(TerrainTarget("1", bumpy, world), at, 10f)
    }

    @Test
    fun theCircleMatchesTheOldOneOnAnIdentityEntity() {
        assertSameCircle(TerrainTarget("1", bumpy, Matrix4()), Vector3(50f, 0f, 50f), 12f)
    }

    @Test
    fun aCircleHangingOverTheEdgeDrawsOnlyTheSegmentsOnTheTerrain() {
        val terrain = TerrainTarget("1", bumpy, Matrix4())
        val lines = Recording()
        BrushCursor(terrain, Vector3(2f, 0f, 50f), 20f).draw(lines)
        assertTrue(lines.segments.size in 1 until 64)
        assertEquals(reference(terrain, Vector3(2f, 0f, 50f), 20f).size, lines.segments.size)
    }

    @Test
    fun aDegenerateEntityMatrixDrawsNothingAndDoesNotThrow() {
        val flat = Matrix4().setToScaling(0f, 1f, 1f)
        val lines = Recording()
        BrushCursor(TerrainTarget("1", bumpy, flat), Vector3(10f, 0f, 10f), 5f).draw(lines)
        assertTrue(lines.segments.isEmpty())
    }
}
