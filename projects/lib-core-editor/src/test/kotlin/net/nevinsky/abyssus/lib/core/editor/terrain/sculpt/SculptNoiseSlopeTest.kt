/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.terrain.FastNoiseSamplerFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.tan

class SculptNoiseSlopeTest {
    private val size = 64
    private val factory = FastNoiseSamplerFactory()

    private fun draft(resolution: Int, f: (Int, Int) -> Float = { _, _ -> 0f }): SculptDraft {
        val base = FloatArray(resolution * resolution) { f(it % resolution, it / resolution) }
        return SculptDraft(resolution, base, base.copyOf(), FloatArray(base.size))
    }

    private fun press(d: SculptDraft, tool: SculptTool, radius: Float, strength: Float = 1f) {
        val at = Vector3(32f, 0f, 32f)
        SculptBrush(d, size, Matrix4()).stroke(at, at, radius, strength, tool, false)
    }

    @Test
    fun `the same noise stroke twice gives identical bytes`() {
        val a = draft(33).also { press(it, noiseTool(factory, 7, 20f, 4f), 12f) }
        val b = draft(33).also { press(it, noiseTool(factory, 7, 20f, 4f), 12f) }
        assertArrayEquals(a.composed, b.composed, 0f)
        assertTrue(a.composed.any { it != 0f })
    }

    @Test
    fun `another seed gives other heights`() {
        val a = draft(33).also { press(it, noiseTool(factory, 7, 20f, 4f), 12f) }
        val b = draft(33).also { press(it, noiseTool(factory, 8, 20f, 4f), 12f) }
        assertTrue(!a.composed.contentEquals(b.composed))
    }

    @Test
    fun `two resolutions agree at the same world point`() {
        val coarse = draft(33).also { press(it, noiseTool(factory, 3, 25f, 5f), 12f) } // cell 2
        val fine = draft(65).also { press(it, noiseTool(factory, 3, 25f, 5f), 12f) } // cell 1
        // the press centre (32,32) is vertex 16 and 32; one cell off, vertices 17 and 34 sit at world 34
        assertEquals(coarse.composed[16 * 33 + 16], fine.composed[32 * 65 + 32], 1e-5f)
        assertTrue(abs(coarse.composed[16 * 33 + 16]) > 0f)
    }

    @Test
    fun `slope limit brings a spike under the limit and leaves a gentle slope alone`() {
        val limit = 30f
        val allowed = tan(Math.toRadians(limit.toDouble())).toFloat() * 2f // cell is 2 world units
        val d = draft(33) { x, z -> if (x == 16 && z == 16) 4f else 0f }
        press(d, SlopeLimitTool(limit, iterations = 64), 24f)
        val at = 16 * 33 + 16
        for (n in intArrayOf(at - 1, at + 1, at - 33, at + 33)) {
            assertTrue("slope ${d.composed[at] - d.composed[n]}", abs(d.composed[at] - d.composed[n]) <= allowed * 1.05f) // neighbours weigh a little under 1, so the fall-off leaves a small remainder
        }
        assertTrue(d.composed[at] < 4f)

        val gentle = draft(33) { x, _ -> x * 0.1f }
        assertTrue(SculptBrush(gentle, size, Matrix4()).stroke(Vector3(32f, 0f, 32f), Vector3(32f, 0f, 32f), 24f, 1f, SlopeLimitTool(limit), false).isEmpty)
    }

    @Test
    fun `slope limit raises a pit and conserves nothing outside the radius`() {
        val d = draft(33) { x, z -> if (x == 16 && z == 16) -4f else 0f }
        press(d, SlopeLimitTool(30f, iterations = 64), 24f)
        assertTrue(d.composed[16 * 33 + 16] > -4f)
        assertEquals(0f, d.composed[0], 0f)
    }
}
