/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.flight.CONTROL_TENSION
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lines may dip below [CONTROL_TENSION] for a moment near the top; this is far short of the 2 s that ends a flight. */
private const val LONGEST_SLACK = 0.5f

/**
 * Each manoeuvre flown by the bot after the take-off, with real physics, the bundled `Stunter` as committed and the
 * normal judge, which reads the flown track on its own: no sample is made up and nothing moves the plane but its forces.
 * A positive mark means the judge recognised the manoeuvre with its repeats, after a long enough pause.
 *
 * The bot turns over between upright and inverted flight with half a wingover, which the judge passes over only once the
 * wingover itself is behind it; the manoeuvres that need a turn-over are flown after the wingover, as in the schedule.
 */
class F2bAiFiguresTest {
    private fun flies(figure: Figure, after: List<Figure> = emptyList()) = AiFlight(schedule = listOf(Figure.TAKEOFF) + after + figure).use { f ->
        f.fly(limit = 160f) { f.entry(figure) != null }
        val report = f.describe()
        val entry = checkNotNull(f.entry(figure)) { "not decided\n$report" }
        assertNull(report, entry.reason)
        assertTrue(report, entry.tenths >= 10)
        assertNull("the flight goes on\n$report", f.result)
        assertTrue("diagnostics\n$report", f.bot.planner.diagnostics.isEmpty())
        assertTrue("the lines stay taut\n$report", f.longestSlack < LONGEST_SLACK)
        // the manoeuvres before it keep their marks, and nothing between is taken for another manoeuvre
        for (before in listOf(Figure.TAKEOFF) + after) assertTrue("${before.label}\n$report", (f.entry(before)?.tenths ?: 0) >= 10)
        for (other in Figure.entries) if (other != figure && other != Figure.TAKEOFF && other !in after) {
            val e = f.entry(other)
            assertTrue("${other.label}\n$report", e == null || e.tenths == 0)
        }
    }

    @Test
    fun reverseWingover() = flies(Figure.REVERSE_WINGOVER)

    @Test
    fun insideLoops() = flies(Figure.INSIDE_LOOPS)

    @Test
    fun invertedLaps() = flies(Figure.INVERTED_LAPS, after = listOf(Figure.REVERSE_WINGOVER))

    @Test
    fun outsideLoops() = flies(Figure.OUTSIDE_LOOPS, after = listOf(Figure.REVERSE_WINGOVER))

    @Test
    fun insideSquares() = flies(Figure.INSIDE_SQUARES)

    @Test
    fun outsideSquares() = flies(Figure.OUTSIDE_SQUARES, after = listOf(Figure.REVERSE_WINGOVER))

    @Test
    fun insideTriangles() = flies(Figure.INSIDE_TRIANGLES)

    @Test
    fun horizontalEights() = flies(Figure.HORIZONTAL_EIGHTS)

    @Test
    fun squareEights() = flies(Figure.SQUARE_EIGHTS)

    @Test
    fun verticalEights() = flies(Figure.VERTICAL_EIGHTS)

    @Test
    fun hourglass() = flies(Figure.HOURGLASS)

    @Test
    fun overheadEights() = flies(Figure.OVERHEAD_EIGHTS)

    @Test
    fun clover() = flies(Figure.CLOVER)
}
