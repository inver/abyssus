/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SculptErosionTest {
    private val resolution = 33
    private val size = 64

    private fun hill(): SculptDraft {
        val base = FloatArray(resolution * resolution) { i ->
            val dx = i % resolution - 16
            val dz = i / resolution - 16
            maxOf(0f, 12f - 2f * kotlin.math.sqrt((dx * dx + dz * dz).toFloat()))
        }
        return SculptDraft(resolution, base, base.copyOf(), FloatArray(base.size))
    }

    private fun erode(d: SculptDraft, strength: Float = 1f, talus: Float = 20f) {
        val at = Vector3(32f, 0f, 32f)
        SculptBrush(d, size, Matrix4()).stroke(at, at, 30f, strength, ThermalErosionTool(talus), false)
    }

    private fun steepest(h: FloatArray): Float {
        var worst = 0f
        for (z in 0 until resolution) for (x in 0 until resolution) {
            if (x + 1 < resolution) worst = maxOf(worst, abs(h[z * resolution + x] - h[z * resolution + x + 1]))
            if (z + 1 < resolution) worst = maxOf(worst, abs(h[z * resolution + x] - h[(z + 1) * resolution + x]))
        }
        return worst
    }

    @Test
    fun `erosion is deterministic`() {
        val a = hill().also { erode(it) }
        val b = hill().also { erode(it) }
        assertArrayEquals(a.composed, b.composed, 0f)
    }

    @Test
    fun `mass is conserved within float tolerance`() {
        val d = hill()
        val before = d.composed.sumOf { it.toDouble() }
        erode(d)
        assertEquals(before, d.composed.sumOf { it.toDouble() }, 1e-2)
        assertTrue(d.delta.any { it != 0f })
    }

    @Test
    fun `slopes decrease`() {
        val d = hill()
        val before = steepest(d.composed)
        erode(d)
        assertTrue(steepest(d.composed) < before)
    }

    @Test
    fun `terrain gentler than the talus angle is left alone`() {
        val flat = SculptDraft(resolution, FloatArray(1089) { (it % resolution) * 0.1f }, FloatArray(1089) { (it % resolution) * 0.1f }, FloatArray(1089))
        val at = Vector3(32f, 0f, 32f)
        assertTrue(SculptBrush(flat, size, Matrix4()).stroke(at, at, 30f, 1f, ThermalErosionTool(30f), false).isEmpty)
    }

    @Test
    fun `vertices outside the radius do not move`() {
        val d = hill()
        erode(d)
        assertEquals(0f, d.delta[0], 0f)
        assertEquals(0f, d.delta[resolution * resolution - 1], 0f)
    }
}
