/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.brush.BrushStamp
import net.nevinsky.abyssus.lib.core.editor.brush.BrushTool
import net.nevinsky.abyssus.lib.core.editor.brush.StampBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A drag keeps stamping into the same arrays once its largest stamp has been seen: the hot path of a sculpt drag. */
class SculptBrushAllocationTest {
    private val resolution = 255
    private val size = 254

    /** Raise/Lower's arithmetic, recording which arrays the engine handed it. */
    private class Recording : BrushTool<SculptGrid> {
        val footprintArrays = ArrayList<Any>()
        val changeArrays = ArrayList<Any>()
        override fun changes(stamp: BrushStamp<SculptGrid>, out: FloatArray) {
            footprintArrays += stamp.footprint.gx
            changeArrays += out
            for (i in 0 until stamp.footprint.count) out[i] = 0.5f * stamp.footprint.weight[i]
        }
    }

    @Test
    fun `two hundred stamps of a large radius reuse the footprint and change arrays after the first`() {
        val base = FloatArray(resolution * resolution)
        val draft = SculptDraft(resolution, base, base.copyOf(), FloatArray(base.size))
        val brush = StampBrush(SculptTarget(draft, size), Matrix4())
        val tool = Recording()
        val from = Vector3(40f, 0f, 127f)
        val to = Vector3(214f, 0f, 127f)
        // radius 40 spaces stamps 10 apart: 174 / 10 stamps along the drag, then back, then again
        brush.stroke(from, to, 40f, 1f, tool, false)
        brush.stroke(to, from, 40f, 1f, tool, false)
        brush.stroke(from, to, 40f, 1f, tool, false)
        assertTrue("only ${tool.changeArrays.size} stamps", tool.changeArrays.size >= 50)
        // the footprint of the first stamp is a full circle, so nothing larger comes later: one array for all of them
        val first = tool.footprintArrays.first()
        val firstChanges = tool.changeArrays.first()
        for (i in 1 until tool.changeArrays.size) {
            assertSame("stamp $i reallocated the footprint", first, tool.footprintArrays[i])
            assertSame("stamp $i reallocated the changes", firstChanges, tool.changeArrays[i])
        }
        assertEquals(1, tool.footprintArrays.distinct().size)
    }
}
