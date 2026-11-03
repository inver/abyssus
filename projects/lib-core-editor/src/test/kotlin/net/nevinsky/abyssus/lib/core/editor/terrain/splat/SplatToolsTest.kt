/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SplatToolsTest {
    private val resolution = 101
    private val size = 100

    private fun draft() = SplatDraft(resolution, size)

    private fun at(d: SplatDraft, gx: Int, gz: Int, c: Int) = d.grid.at(gx, gz, c)

    private fun click(d: SplatDraft, tool: SplatTool, x: Float, z: Float, radius: Float = 10f, strength: Float = 1f,
                      reversed: Boolean = false, exclusive: Boolean = true, world: Matrix4 = Matrix4()): Vector3 {
        val p = Vector3(x, 0f, z)
        SplatBrush(d, exclusive, world).stroke(p, p, radius, strength, tool, reversed)
        return p
    }

    @Test
    fun `paint raises only the slot channel, strongest at the centre and nothing beyond the radius`() {
        val d = draft()
        click(d, SplatTool.Paint(2), 50f, 50f)
        val centre = at(d, 50, 50, 1)
        assertTrue(centre > 0)
        assertTrue(at(d, 53, 50, 1) < centre)
        assertEquals(0, at(d, 50, 50, 0))
        assertEquals(0, at(d, 50, 50, 2))
        assertEquals(0, at(d, 50, 50, 3))
        assertEquals(0, at(d, 61, 50, 1))
        assertEquals(0, at(d, 50, 39, 1))
    }

    @Test
    fun `reversed paint lowers only that channel`() {
        val d = draft()
        d.pixels.indices.forEach { d.pixels[it] = 100 }
        click(d, SplatTool.Paint(1), 50f, 50f, reversed = true)
        assertTrue(at(d, 50, 50, 0) < 100)
        assertEquals(100, at(d, 50, 50, 1))
        assertEquals(100, at(d, 50, 50, 3))
    }

    @Test
    fun `the base slot acts as erase`() {
        val d = draft()
        d.pixels.indices.forEach { d.pixels[it] = 100 }
        click(d, SplatTool.Paint(0), 50f, 50f)
        for (c in 0 until 4) assertTrue(at(d, 50, 50, c) < 100)
    }

    @Test
    fun `erase lowers all four channels and reports nothing on an empty map`() {
        val d = draft()
        click(d, SplatTool.Erase, 50f, 50f)
        assertTrue(d.changed.isEmpty)
        assertEquals(0, d.revision)
        d.pixels.indices.forEach { d.pixels[it] = 80 }
        click(d, SplatTool.Erase, 50f, 50f)
        for (c in 0 until 4) assertTrue(at(d, 50, 50, c) < 80)
        assertEquals(80, at(d, 70, 70, 0))
    }

    @Test
    fun `smooth leaves a flat area unchanged and keeps the sum within 255`() {
        val d = draft()
        for (t in 0 until resolution * resolution) {
            d.pixels[t * 4] = 100; d.pixels[t * 4 + 1] = 100; d.pixels[t * 4 + 2] = 50
        }
        click(d, SplatTool.Smooth, 50f, 50f)
        assertTrue(d.changed.isEmpty)
        // a hard edge: left half R 255, right half G 255
        val e = draft()
        for (gz in 0 until resolution) for (gx in 0 until resolution) {
            e.pixels[(gz * resolution + gx) * 4 + if (gx < 50) 0 else 1] = -1
        }
        click(e, SplatTool.Smooth, 50f, 50f, exclusive = false)
        assertTrue(at(e, 49, 50, 0) < 255)
        assertTrue(at(e, 50, 50, 1) < 255)
        for (gz in 0 until resolution) for (gx in 0 until resolution) {
            assertTrue((0 until 4).sumOf { at(e, gx, gz, it) } <= 255)
        }
    }

    @Test
    fun `a scaled and yaw rotated entity paints under the drawn circle`() {
        val world = Matrix4().set(Vector3(20f, 3f, -10f), Quaternion(Vector3.Y, 30f), Vector3(2f, 1f, 2f))
        val d = draft()
        val centreLocal = Vector3(50f, 0f, 50f)
        val centreWorld = Vector3(centreLocal).mul(world)
        SplatBrush(d, true, world).stroke(centreWorld, centreWorld, 10f, 1f, SplatTool.Paint(1), false)
        var painted = 0
        for (gz in 0 until resolution) for (gx in 0 until resolution) {
            val w = Vector3(gx.toFloat(), 0f, gz.toFloat()).mul(world)
            val dist = hypot(w.x - centreWorld.x, w.z - centreWorld.z)
            if (at(d, gx, gz, 0) > 0) {
                painted++
                assertTrue("texel $gx,$gz at $dist", dist < 10f)
            } else assertFalse(dist < 6f)
        }
        assertTrue(painted > 4)
        assertTrue(at(d, 50, 50, 0) > 0)
    }
}
