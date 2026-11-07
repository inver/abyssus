/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modellights

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun piece(x: Float, size: Float = 1f) = LampPiece(Vector3(x, 0f, 0f), Vector3(size, size, size))

class LampLightsTest {
    @Test
    fun colorIsEmissiveOrWhite() {
        assertEquals(0.8f, lampColor(Color(0.4f, 0.8f, 1f, 1f)).g, 0f)
        assertEquals(Color(1f, 1f, 1f, 1f), lampColor(null))
        assertEquals(Color(1f, 1f, 1f, 1f), lampColor(Color(0f, 0f, 0f, 1f)))
    }

    @Test
    fun aLargerLampHasALongerRangeUntilTheMinimumApplies() {
        assertEquals(LAMP_MIN_RANGE, lampRange(piece(0f, 0.1f)), 0f)
        assertEquals(2 * lampRange(piece(0f, 10f)), lampRange(piece(0f, 20f)), 1e-4f)
    }

    @Test
    fun underTheCapNothingIsMerged() {
        val capped = capPieces(listOf(piece(0f), piece(10f)), 5)
        assertFalse(capped.merged)
        assertEquals(2, capped.pieces.size)
    }

    @Test
    fun overTheCapTheNearestAreMergedToExactlyTheMaximum() {
        val pieces = listOf(0f, 1f, 10f, 20f, 21f).map { piece(it) }
        val capped = capPieces(pieces, 3)
        assertTrue(capped.merged)
        assertEquals(listOf(0.5f, 10f, 20.5f), capped.pieces.map { it.center.x })
        assertEquals(2f, capped.pieces[0].size.x, 1e-6f)
        assertEquals(capped.pieces.map { it.center.x }, capPieces(pieces, 3).pieces.map { it.center.x })
    }

    @Test
    fun manyPiecesAreReducedToTheMaximum() {
        val capped = capPieces((0 until 3000).map { piece(it * 2f) }, LAMP_LIGHT_MAX)
        assertEquals(LAMP_LIGHT_MAX, capped.pieces.size)
        assertTrue(capped.merged)
    }
}
