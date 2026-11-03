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

class SculptStampTest {
    private val resolution = 33
    private val size = 64

    private fun draft(f: (Int, Int) -> Float = { _, _ -> 0f }): SculptDraft {
        val base = FloatArray(resolution * resolution) { f(it % resolution, it / resolution) }
        return SculptDraft(resolution, base, base.copyOf(), FloatArray(base.size))
    }

    /** Dark on the left half, white on the right half. */
    private val image = StampImage(2, 1, floatArrayOf(0f, 1f))
    private val centre = Vector3(32f, 0f, 32f)

    private fun stamp(d: SculptDraft, tool: StampTool, strength: Float = 1f, to: Vector3 = centre) =
        SculptBrush(d, size, Matrix4()).stroke(centre, to, 1f, strength, tool, false)

    @Test
    fun `bilinear sampling blends neighbouring pixels`() {
        assertEquals(0f, image.sample(0f, 0.5f), 1e-6f)
        assertEquals(0.5f, image.sample(0.5f, 0.5f), 1e-6f)
        assertEquals(1f, image.sample(1f, 0.5f), 1e-6f)
        assertEquals(0.25f, image.sample(0.375f, 0.5f), 1e-6f)
    }

    @Test
    fun `add places the scaled image at the click and leaves outside the square alone`() {
        val d = draft()
        stamp(d, StampTool(image, 20f, 10f, 0f, StampBlend.ADD))
        // the image is 20 wide: world x 22..42; the right end (x = 42) is white, scaled to 10
        assertEquals(10f, d.composed[16 * resolution + 21], 0.7f)
        assertEquals(0f, d.composed[16 * resolution + 10], 0f)
        assertEquals(0f, d.composed[0], 0f)
        assertEquals(5f, d.composed[16 * resolution + 16], 0.2f) // the middle samples 0.5
    }

    @Test
    fun `rotation turns the image`() {
        val d = draft()
        stamp(d, StampTool(image, 20f, 10f, 180f, StampBlend.ADD))
        assertTrue(d.composed[16 * resolution + 11] > d.composed[16 * resolution + 21]) // white is now on the left
    }

    @Test
    fun `max only raises and min only lowers toward the target`() {
        // the target is the height under the click plus the image value (0 on the left, 10 on the right)
        val high = draft { x, _ -> if (x < 16) 40f else 20f }
        stamp(high, StampTool(image, 20f, 10f, 0f, StampBlend.MAX))
        assertEquals(40f, high.composed[16 * resolution + 12], 0f) // already above the target: kept
        assertTrue(high.composed[16 * resolution + 21] > 25f) // below the target: raised

        val low = draft()
        stamp(low, StampTool(image, 20f, 10f, 0f, StampBlend.MIN))
        assertTrue(low.delta.all { it == 0f }) // flat ground is never above the target

        val bump = draft { x, z -> if (x == 21 && z == 16) 50f else 0f }
        stamp(bump, StampTool(image, 20f, 10f, 0f, StampBlend.MIN))
        assertTrue(bump.composed[16 * resolution + 21] < 50f) // above the target: lowered
    }

    @Test
    fun `one click is one stamp however far it is dragged`() {
        val clicked = draft().also { stamp(it, StampTool(image, 20f, 10f, 0f, StampBlend.ADD)) }
        val dragged = draft().also { stamp(it, StampTool(image, 20f, 10f, 0f, StampBlend.ADD), to = Vector3(60f, 0f, 32f)) }
        assertEquals(clicked.composed.toList(), dragged.composed.toList())
    }

    @Test
    fun `strength scales the stamp`() {
        val d = draft()
        stamp(d, StampTool(image, 20f, 10f, 0f, StampBlend.ADD), strength = 0.5f)
        assertEquals(2.5f, d.composed[16 * resolution + 16], 0.2f)
    }
}
