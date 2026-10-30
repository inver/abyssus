/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MatchersTest {
    private val ideal = IdealTracks(18f)
    private val matchers = Matchers(18f)

    private fun recognise(track: IdealTrack): Recognition? {
        val flight = BurstFlight(track)
        return matchers.recognise(flight.burst, flight.path.points)
    }

    private fun assertFigure(expected: Figure, repeats: Int, verdict: Verdict, track: IdealTrack) {
        val flight = BurstFlight(track)
        val r = matchers.recognise(flight.burst, flight.path.points)
        assertNotNull("nothing recognised in ${flight.burst.joinToString { it::class.simpleName.orEmpty() }}", r)
        assertEquals("figure ($r)", expected, r!!.figure)
        assertEquals("repeats", repeats, r.repeats)
        assertEquals("verdict", verdict, r.verdict)
    }

    @Test
    fun insideAndOutsideLoops() {
        assertFigure(Figure.INSIDE_LOOPS, 3, Verdict.COMPLETE, ideal.loops(100f, 3))
        assertFigure(Figure.OUTSIDE_LOOPS, 3, Verdict.COMPLETE, ideal.loops(100f, 3, inverted = true))
        assertFigure(Figure.INSIDE_LOOPS, 2, Verdict.WRONG_REPEATS, ideal.loops(100f, 2))
        assertFigure(Figure.INSIDE_LOOPS, 4, Verdict.WRONG_REPEATS, ideal.loops(100f, 4))
    }

    @Test
    fun loopsAreAnchoredAtTheirFirstVertical() {
        val r = recognise(ideal.loops(100f, 3))!!
        assertEquals(100f, r.anchor, 3f)
    }

    @Test
    fun squares() {
        assertFigure(Figure.INSIDE_SQUARES, 2, Verdict.COMPLETE, ideal.squares(80f, 2))
        assertFigure(Figure.OUTSIDE_SQUARES, 2, Verdict.COMPLETE, ideal.squares(80f, 2, inverted = true))
        assertFigure(Figure.INSIDE_SQUARES, 1, Verdict.WRONG_REPEATS, ideal.squares(80f, 1))
        assertFigure(Figure.INSIDE_SQUARES, 3, Verdict.WRONG_REPEATS, ideal.squares(80f, 3))
        assertEquals(80f, recognise(ideal.squares(80f, 2))!!.anchor, 3f)
    }

    @Test
    fun roundedCornersStillReadAsSquares() {
        assertFigure(Figure.INSIDE_SQUARES, 2, Verdict.COMPLETE, IdealTracks(18f, cornerMetres = 2.5f).squares(80f, 2))
    }

    @Test
    fun squaresAreNotLoops() {
        val r = recognise(ideal.squares(80f, 2))!!
        assertEquals(Figure.INSIDE_SQUARES, r.figure)
    }

    @Test
    fun triangles() {
        assertFigure(Figure.INSIDE_TRIANGLES, 2, Verdict.COMPLETE, ideal.triangles(100f, 2))
        assertFigure(Figure.INSIDE_TRIANGLES, 1, Verdict.WRONG_REPEATS, ideal.triangles(100f, 1))
        assertFigure(Figure.INSIDE_TRIANGLES, 3, Verdict.WRONG_REPEATS, ideal.triangles(100f, 3))
    }

    @Test
    fun eightsAreNotLoops() {
        assertFigure(Figure.HORIZONTAL_EIGHTS, 2, Verdict.COMPLETE, ideal.horizontalEights(60f, 2, connected = true))
        assertFigure(Figure.HORIZONTAL_EIGHTS, 1, Verdict.WRONG_REPEATS, ideal.horizontalEights(60f, 1, connected = true))
        assertFigure(Figure.HORIZONTAL_EIGHTS, 3, Verdict.WRONG_REPEATS, ideal.horizontalEights(60f, 3, connected = true))
    }

    @Test
    fun squareEights() {
        assertFigure(Figure.SQUARE_EIGHTS, 2, Verdict.COMPLETE, ideal.squareEights(60f, 2, connected = true))
        assertFigure(Figure.SQUARE_EIGHTS, 3, Verdict.WRONG_REPEATS, ideal.squareEights(60f, 3, connected = true))
    }

    @Test
    fun verticalEights() {
        assertFigure(Figure.VERTICAL_EIGHTS, 2, Verdict.COMPLETE, ideal.verticalEights(60f, 2))
        assertFigure(Figure.VERTICAL_EIGHTS, 1, Verdict.WRONG_REPEATS, ideal.verticalEights(60f, 1))
    }

    @Test
    fun overheadEightsAreCountedThroughTheZenith() {
        assertFigure(Figure.OVERHEAD_EIGHTS, 2, Verdict.COMPLETE, ideal.overheadEights(60f, 2, connected = true))
        assertFigure(Figure.OVERHEAD_EIGHTS, 3, Verdict.WRONG_REPEATS, ideal.overheadEights(60f, 3, connected = true))
    }

    @Test
    fun hourglassAndClover() {
        assertFigure(Figure.HOURGLASS, 1, Verdict.COMPLETE, ideal.hourglass(150f))
        assertFigure(Figure.CLOVER, 4, Verdict.COMPLETE, ideal.clover(270f, connected = true))
    }

    @Test
    fun reverseWingover() {
        assertFigure(Figure.REVERSE_WINGOVER, 2, Verdict.COMPLETE, ideal.reverseWingover(30f))
    }

    @Test
    fun nothingInPlainLevelFlight() {
        val flight = SyntheticFlight()
        flight.level(to = 100f, from = 0f, laps = 2f)
        val path = PathTokens(18f)
        for (s in flight.samples) path.feed(s)
        path.flush()
        assertNull(matchers.recognise(path.tokens.filter { it !is Level }, path.points))
    }
}
