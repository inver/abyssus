/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SculptRaiseTest {
    private val resolution = 33
    private val size = 64 // a grid point every 2 world units

    private fun draft() = SculptDraft(resolution, FloatArray(resolution * resolution), FloatArray(resolution * resolution), FloatArray(resolution * resolution))

    /** The world point above the terrain-local [x],[z] of an entity placed by [world]. */
    private fun worldOf(world: Matrix4, x: Float, z: Float) = Vector3(x, 0f, z).mul(world)

    private fun press(d: SculptDraft, world: Matrix4, x: Float, z: Float, radius: Float, strength: Float, inverted: Boolean = false) {
        val at = worldOf(world, x, z)
        SculptBrush(d, size, world).stroke(at, at, radius, strength, RaiseLowerTool(), inverted)
    }

    @Test
    fun `the centre rises most and nothing at or beyond the radius changes`() {
        val d = draft()
        press(d, Matrix4(), 32f, 32f, 10f, 1f)
        val centre = d.composed[16 * resolution + 16]
        assertTrue(centre > 0f)
        for (z in 0 until resolution) for (x in 0 until resolution) {
            val dist = sqrt(((x - 16) * 2f) * ((x - 16) * 2f) + ((z - 16) * 2f) * ((z - 16) * 2f))
            val h = d.composed[z * resolution + x]
            if (dist >= 10f) assertEquals("($x,$z)", 0f, h, 0f) else assertTrue(h <= centre)
        }
    }

    @Test
    fun `shift lowers`() {
        val d = draft()
        press(d, Matrix4(), 32f, 32f, 10f, 1f, inverted = true)
        assertTrue(d.composed[16 * resolution + 16] < 0f)
    }

    @Test
    fun `strength scales the change`() {
        val full = draft().also { press(it, Matrix4(), 32f, 32f, 10f, 1f) }
        val half = draft().also { press(it, Matrix4(), 32f, 32f, 10f, 0.5f) }
        assertTrue(half.composed[16 * resolution + 16] < full.composed[16 * resolution + 16])
        assertTrue(half.composed[16 * resolution + 16] > 0f)
    }

    @Test
    fun `a rotated and scaled entity is sculpted under the drawn circle`() {
        val world = Matrix4().setToTranslation(100f, 5f, -40f).rotate(Vector3.Y, 33f).scl(2f, 1f, 2f)
        val d = draft()
        val radius = 12f
        press(d, world, 30f, 20f, radius, 1f)
        val centreWorld = worldOf(world, 30f, 20f)
        var changed = 0
        for (z in 0 until resolution) for (x in 0 until resolution) {
            val p = worldOf(world, x * 2f, z * 2f)
            val dist = sqrt((p.x - centreWorld.x) * (p.x - centreWorld.x) + (p.z - centreWorld.z) * (p.z - centreWorld.z))
            val h = d.composed[z * resolution + x]
            if (dist >= radius) assertEquals("outside ($x,$z)", 0f, h, 0f)
            else if (h != 0f) changed++
        }
        assertTrue(changed > 10)
    }

    @Test
    fun `a stroke that changes nothing reports an empty rectangle`() {
        val d = draft()
        val at = Vector3(32f, 0f, 32f)
        assertTrue(SculptBrush(d, size, Matrix4()).stroke(at, at, 10f, 0f, RaiseLowerTool(), false).isEmpty)
        assertEquals(0, d.revision)
    }
}
