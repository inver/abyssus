/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.brush.BrushStamp
import net.nevinsky.abyssus.lib.core.editor.brush.BrushTool
import net.nevinsky.abyssus.lib.core.editor.brush.ByteGrid
import net.nevinsky.abyssus.lib.core.editor.brush.StampBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A drag keeps stamping into the same arrays once its largest stamp has been seen: the hot path of a splat drag. */
class SplatBrushAllocationTest {
    private val resolution = 1024
    private val size = 1023

    private class Recording : BrushTool<ByteGrid> {
        override val channels = 4
        val footprintArrays = ArrayList<Any>()
        val changeArrays = ArrayList<Any>()
        override fun changes(stamp: BrushStamp<ByteGrid>, out: FloatArray) {
            footprintArrays += stamp.footprint.gx
            changeArrays += out
            for (i in 0 until stamp.footprint.count) out[i * 4 + 1] = 0.5f * stamp.footprint.weight[i]
        }
    }

    @Test
    fun `fifty stamps of radius 40 reuse the footprint and change arrays after the first`() {
        val brush = StampBrush(SplatTarget(SplatDraft(resolution, size), exclusive = true), Matrix4())
        val tool = Recording()
        val from = Vector3(100f, 0f, 500f)
        val to = Vector3(300f, 0f, 500f)
        brush.stroke(from, to, 40f, 1f, tool, false)
        brush.stroke(to, from, 40f, 1f, tool, false)
        brush.stroke(from, to, 40f, 1f, tool, false)
        assertTrue("only ${tool.changeArrays.size} stamps", tool.changeArrays.size >= 50)
        for (i in 1 until tool.changeArrays.size) {
            assertSame("stamp $i reallocated the footprint", tool.footprintArrays.first(), tool.footprintArrays[i])
            assertSame("stamp $i reallocated the changes", tool.changeArrays.first(), tool.changeArrays[i])
        }
        assertEquals(1, tool.footprintArrays.distinct().size)
    }
}
