/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathTokensTest {
    private val ideal = IdealTracks(18f)

    private fun tokensOf(samples: List<TrackSample>): List<Token> {
        val path = PathTokens(18f)
        for (s in samples) path.feed(s)
        path.flush()
        return path.tokens
    }

    /** The tokens of [track] flown with level flight at the base before and after it (so no turn is cut short). */
    private fun flyTokens(track: IdealTrack): List<Token> {
        val flight = SyntheticFlight()
        val sphere = HemisphereGeometry(18f)
        val start = sphere.azimuth(track.points.first().u)
        flight.level(to = start, from = start - 40f, inverted = track.points.first().inverted)
        flight.fly(track)
        flight.level(to = flight.endAzimuth + 40f, inverted = track.points.last().inverted)
        return tokensOf(flight.samples)
    }

    @Test
    fun anInsideLoopReadsAsOneInsideArcOfThreeHundredAndSixtyDegrees() {
        val arcs = flyTokens(ideal.loops(vertical = 40f, count = 1)).filterIsInstance<Arc>()
        assertEquals(1, arcs.size)
        assertEquals(Side.INSIDE, arcs[0].side)
        assertTrue("turned ${arcs[0].angle}", arcs[0].angle in 300f..365f)
    }

    @Test
    fun anInvertedOutsideLoopReadsAsAnOutsideArc() {
        val arcs = flyTokens(ideal.loops(vertical = 40f, count = 1, inverted = true)).filterIsInstance<Arc>()
        assertEquals(1, arcs.size)
        assertEquals(Side.OUTSIDE, arcs[0].side)
        assertTrue("turned ${arcs[0].angle}", arcs[0].angle in 300f..365f)
    }

    @Test
    fun threeLoopsReadAsThreeArcs() {
        val arcs = flyTokens(ideal.loops(vertical = 40f, count = 3)).filterIsInstance<Arc>()
        // the turn goes on round the circles without a straight, so the arcs join into one long turn or three
        val total = arcs.sumOf { it.angle.toDouble() }
        assertTrue("turned $total", total in 950.0..1100.0)
        assertTrue(arcs.all { it.side == Side.INSIDE })
    }

    @Test
    fun aSquareReadsAsFourCornersAndFourStraights() {
        val tokens = flyTokens(ideal.squares(climb = 80f, count = 1))
        val corners = tokens.filterIsInstance<Corner>()
        assertEquals(tokens.joinToString { it::class.simpleName.orEmpty() }, 4, corners.size)
        assertTrue(corners.all { it.side == Side.INSIDE && it.angle in 70f..110f })
        val vertical = tokens.filterIsInstance<Straight>().filter { it.offVertical < 8f }
        assertEquals(2, vertical.size)
        // the top is level flight along the 45 degree parallel
        assertTrue(tokens.filterIsInstance<Level>().any { it.latitude in 40f..50f })
    }

    @Test
    fun roundedCornersOfTwoAndAHalfMetresStillReadAsCorners() {
        val track = IdealTracks(18f, cornerMetres = 2.5f).squares(climb = 80f, count = 1)
        val corners = flyTokens(track).filterIsInstance<Corner>()
        assertEquals(4, corners.size)
    }

    @Test
    fun aTriangleReadsAsThreeCorners() {
        val corners = flyTokens(ideal.triangles(start = 100f, count = 1)).filterIsInstance<Corner>()
        assertEquals(3, corners.size)
        assertTrue(corners.all { it.angle in 90f..130f })
    }

    @Test
    fun aPathThroughTheZenithKeepsItsSide() {
        val tokens = flyTokens(ideal.overheadEights(axis = 60f, count = 1, connected = true))
        val arcs = tokens.filterIsInstance<Arc>()
        assertEquals(tokens.joinToString { it::class.simpleName.orEmpty() }, 2, arcs.size)
        assertEquals(Side.INSIDE, arcs[0].side)
        assertEquals(Side.OUTSIDE, arcs[1].side)
    }

    @Test
    fun theWingoverReadsAsStraightsOverTheZenithAndInvertedLevelFlight() {
        val tokens = flyTokens(ideal.reverseWingover(climb = 30f))
        val passes = tokens.filterIsInstance<Straight>().filter { it.offVertical < 8f }
        assertEquals(2, passes.count { it.length > 30f })
        val inverted = tokens.filterIsInstance<Level>().filter { !it.upright }
        assertTrue(inverted.isNotEmpty() && inverted.sumOf { it.azimuthSpan.toDouble() } > 160.0)
    }

    @Test
    fun theGroundRollAndTheTouchdown() {
        val path = PathTokens(18f)
        val flight = SyntheticFlight()
        for (s in flight.fly(ideal.takeoff(release = 0f))) path.feed(s)
        for (s in flight.fly(ideal.landing(start = flight.endAzimuth), engineRunning = false)) path.feed(s)
        path.flush()
        assertTrue(path.tokens.first() is GroundRoll)
        assertTrue(path.tokens.any { it is Touchdown })
    }
}
