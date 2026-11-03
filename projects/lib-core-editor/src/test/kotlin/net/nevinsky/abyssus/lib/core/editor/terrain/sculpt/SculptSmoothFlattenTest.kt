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
import kotlin.math.abs

class SculptSmoothFlattenTest {
    private val resolution = 33
    private val size = 64

    private fun draft(f: (Int, Int) -> Float = { _, _ -> 0f }): SculptDraft {
        val base = FloatArray(resolution * resolution) { f(it % resolution, it / resolution) }
        return SculptDraft(resolution, base, base.copyOf(), FloatArray(base.size))
    }

    private fun stroke(d: SculptDraft, tool: SculptTool, radius: Float = 10f, from: Vector3 = Vector3(32f, 0f, 32f), to: Vector3 = from, strength: Float = 1f) =
        SculptBrush(d, size, Matrix4()).stroke(from, to, radius, strength, tool, false)

    @Test
    fun `smooth on a flat area is an empty stroke`() {
        val d = draft { _, _ -> 7f }
        assertTrue(stroke(d, SmoothTool()).isEmpty)
        assertEquals(0, d.revision)
    }

    @Test
    fun `smooth lowers a spike and keeps the rest within the radius`() {
        val d = draft { x, z -> if (x == 16 && z == 16) 10f else 0f }
        stroke(d, SmoothTool())
        assertTrue(d.composed[16 * resolution + 16] < 10f)
        assertEquals(0f, d.composed[0], 0f)
    }

    @Test
    fun `smooth does not depend on the visiting order`() {
        val d = draft { x, z -> ((x * 7 + z * 13) % 5).toFloat() }
        val before = d.composed.copyOf()
        stroke(d, SmoothTool())
        // every change is the neighbour average of the heights from before the stamp
        val at = 16 * resolution + 16
        val expected = before[at] + ((before[at - 1] + before[at + 1] + before[at - resolution] + before[at + resolution]) / 4f - before[at])
        assertEquals(expected, d.composed[at], 1e-5f)
    }

    @Test
    fun `flatten converges to the height where the stroke began`() {
        val d = draft { x, _ -> x * 0.5f }
        val start = Vector3(32f, 0f, 32f)
        val target = d.composed[16 * resolution + 16]
        repeat(40) { stroke(d, FlattenTool(), from = start, to = Vector3(36f, 0f, 32f)) }
        assertEquals(target, d.composed[16 * resolution + 17], 0.05f)
        assertEquals(0f, d.composed[0], 0f) // far from the brush
    }

    @Test
    fun `set height at full strength reaches the target at the centre and the delta is target minus base`() {
        val d = draft { x, z -> (x + z).toFloat() }
        val baseCentre = d.base[16 * resolution + 16]
        stroke(d, SetHeightTool(50f))
        assertEquals(50f, d.composed[16 * resolution + 16], 1e-4f)
        assertEquals(50f - baseCentre, d.delta[16 * resolution + 16], 1e-4f)
    }

    @Test
    fun `set height leaves a vertex already at the target alone`() {
        val d = draft { _, _ -> 3f }
        assertTrue(stroke(d, SetHeightTool(3f)).isEmpty)
        assertTrue(d.delta.all { abs(it) == 0f })
    }
}
