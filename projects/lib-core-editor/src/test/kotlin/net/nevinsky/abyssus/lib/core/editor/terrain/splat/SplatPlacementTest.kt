/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import net.nevinsky.abyssus.lib.core.editor.brush.BrushPath
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The brush places grid point `i` at `i * size / (resolution - 1)`; the shader reads texel `i` centred at
 * `(i + 0.5) / resolution * size` (`uv = local xz / size`). They differ by at most half a texel, `size / resolution / 2`.
 */
class SplatPlacementTest {
    @Test
    fun `the brush point and the shader texel centre differ by at most half a texel`() {
        for ((resolution, size) in listOf(64 to 200, 1024 to 200, 2048 to 513)) {
            val texel = size.toFloat() / resolution
            for (i in 0 until resolution step maxOf(1, resolution / 97)) {
                val brush = i * size.toFloat() / (resolution - 1)
                val shader = (i + 0.5f) / resolution * size
                assertTrue("$resolution texel $i", abs(brush - shader) <= texel / 2f + 1e-3f)
            }
        }
    }

    @Test
    fun `image row r is grid row r and a stroke at a texel changes that texel`() {
        val resolution = 64
        val size = 63
        val d = SplatDraft(resolution, size)
        // grid point (10, 20): terrain-local x = 10 * cell, z = 20 * cell; with cell 1 that is (10, 20)
        val p = Vector3(10f, 0f, 20f)
        SplatBrush(d, true, Matrix4()).stroke(p, p, 3f, 1f, SplatTool.Paint(1), false)
        val i = (20 * resolution + 10) * 4
        assertTrue((d.pixels[i].toInt() and 0xFF) > 0)
        // the codec writes row r of the array as row r of the image
        val image = SplatImageCodec().decode("a.png", SplatImageCodec().encode(SplatPixels(resolution, d.pixels)))
        assertEquals(d.pixels[i], image.rgba[i])
        assertEquals(1f, BrushPath(size, resolution, Matrix4()).cell, 1e-6f)
    }
}
