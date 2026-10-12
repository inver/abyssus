/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IdealTracksTest {
    private val sphere = HemisphereGeometry(18f)
    private val ideal = IdealTracks(18f)

    private fun IdealTrack.lat(i: Int) = sphere.latitude(points[i].u)
    private fun IdealTrack.latitudes() = points.map { sphere.latitude(it.u) }

    /** The azimuth span (degrees) the track flies, summing its signed azimuth steps. */
    private fun IdealTrack.azimuthTravelled(): Float {
        var sum = 0f
        for (i in 1 until points.size) if (sphere.latitude(points[i].u) < 85f) sum += wrapDeg(sphere.azimuth(points[i].u) - sphere.azimuth(points[i - 1].u))
        return sum
    }

    private fun IdealTrack.length() = points.zipWithNext().sumOf { (a, b) -> sphere.angle(a.u, b.u).toDouble() }.toFloat()

    @Test
    fun loopsAreCirclesBetweenTheBaseAndTheFortyFiveParallel() {
        val track = ideal.loops(vertical = 100f, count = 3)
        assertEquals(0f, track.latitudes().min(), 0.1f)
        assertEquals(45f, track.latitudes().max(), 0.1f)
        assertEquals(3, track.repeats)
        // three superimposed circles: the second lap passes the same places as the first
        val n = track.repeat(0).size
        assertEquals(0f, sphere.angle(track.repeat(0)[n / 3].u, track.repeat(1)[n / 3].u), 0.6f)
        // the first vertical, the east-most point, is at the anchor, and the track starts at the bottom going east
        assertEquals(100f, track.points.take(track.repeat(0).size).maxOf { sphere.azimuth(it.u) }, 0.2f)
        assertTrue(sphere.azimuth(track.points[5].u) > sphere.azimuth(track.points[0].u))
    }

    @Test
    fun wingoverPassesTheZenithAndFliesHalfALapInverted() {
        val track = ideal.reverseWingover(climb = 30f)
        assertEquals(90f, track.latitudes().max(), 0.1f)
        val inverted = track.points.filter { it.inverted }
        // the corners at its ends round off a little of the half lap
        assertEquals(180f, abs(sphere.azimuth(inverted.last().u) - sphere.azimuth(inverted.first().u)), 8f)
        assertTrue(inverted.all { abs(sphere.latitude(it.u)) < 5f })
    }

    @Test
    fun takeoffLevelsOverTheReleasePointAndFliesTwoLapsAtTheBase() {
        val track = ideal.takeoff(release = 10f, roll = 8f)
        assertEquals(ideal.groundLatitude, track.lat(0), 0.01f)
        val base = track.points.filter { it.tag == Tag.BASE }
        assertEquals(720f, abs(base.zipWithNext().sumOf { (a, b) -> wrapDeg(sphere.azimuth(b.u) - sphere.azimuth(a.u)).toDouble() }.toFloat()), 1f)
        assertEquals(10f, sphere.azimuth(base.first().u), 0.5f)
        assertEquals(1080f, track.azimuthTravelled(), 1f)
        assertTrue(track.points.first { it.tag == Tag.ROLL }.let { sphere.lapsToMetres(0.0f) } == 0f)
    }

    @Test
    fun theLandingGlideIsOneLap() {
        val track = ideal.landing(start = 200f)
        assertEquals(360f, track.azimuthTravelled(), 1f)
        assertEquals(0f, track.lat(0), 0.01f)
        assertEquals(ideal.groundLatitude, track.lat(track.points.size - 1), 0.02f)
    }

    @Test
    fun squaresHaveVerticalSidesAndAreAnEighthOfALapWide() {
        val track = ideal.squares(climb = 80f, count = 2)
        assertEquals(2, track.repeats)
        assertEquals(45f, track.latitudes().max(), 0.1f)
        assertEquals(0f, track.latitudes().min(), 0.1f)
        val vertical = track.points.filter { it.tag == Tag.VERTICAL }
        val climbs = vertical.filter { sphere.azimuth(it.u) > 70f }
        val descents = vertical.filter { sphere.azimuth(it.u) < 50f }
        assertTrue(climbs.isNotEmpty() && descents.isNotEmpty())
        assertEquals(80f, climbs.map { sphere.azimuth(it.u) }.average().toFloat(), 0.1f)
        assertEquals(80f - 45f, descents.map { sphere.azimuth(it.u) }.average().toFloat(), 0.1f)
    }

    @Test
    fun trianglesHaveLegsThirtyDegreesOffVerticalAndTheApexOnTheParallel() {
        val track = ideal.triangles(start = 100f, count = 2)
        // the apex corner is rounded, which takes its tip off
        assertEquals(45f, track.latitudes().max(), 2f)
        assertEquals(0f, track.latitudes().min(), 0.1f)
        val leg = track.points.filter { it.tag == Tag.LEG }
        val a = leg[leg.size / 8].u
        val b = leg[leg.size / 8 + 1].u
        // the leg leaves the base 30 degrees off the meridian: its azimuth changes by tan(30 degrees) times its latitude
        val rise = sphere.latitude(b) - sphere.latitude(a)
        val across = (sphere.azimuth(b) - sphere.azimuth(a)) * kotlin.math.cos(Math.toRadians(sphere.latitude(a).toDouble())).toFloat()
        assertTrue(abs(across) / rise < 0.9f && abs(across) / rise > 0.3f)
    }

    @Test
    fun theHourglassCrossesOnTheFortyFiveParallelAndIsSymmetric() {
        val track = ideal.hourglass(axis = 150f)
        val legs = track.points.filter { it.tag == Tag.LEG }
        val onAxis = legs.filter { abs(wrapDeg(sphere.azimuth(it.u) - 150f)) < 0.5f }
        assertTrue(onAxis.isNotEmpty())
        assertEquals(45f, onAxis.map { sphere.latitude(it.u) }.average().toFloat(), 1f)
        // symmetric about the axis: for each point there is one mirrored in azimuth with the same latitude
        for (p in track.points.filter { (it.tag == Tag.LEG || it.tag == Tag.OVER_TOP) && sphere.latitude(it.u) > 10f }.filterIndexed { i, _ -> i % 20 == 0 }) {
            val mirrored = place(sphere.latitude(p.u), 300f - sphere.azimuth(p.u))
            assertTrue(track.points.any { sphere.angle(it.u, mirrored) < 1f })
        }
    }

    @Test
    fun theEightsLobesMeetAtOnePoint() {
        val horizontal = ideal.horizontalEights(crossing = 60f, count = 2)
        assertEquals(0f, horizontal.latitudes().min(), 2.5f)
        assertEquals(45f, horizontal.latitudes().max(), 2.5f)
        val x = horizontal.points[0].u
        val half = horizontal.repeat(0).size / 2
        assertEquals(0f, sphere.angle(x, horizontal.repeat(0)[half].u), 0.3f)
        assertEquals(0f, sphere.angle(x, horizontal.repeat(1).first().u), 0.6f)
        val vertical = ideal.verticalEights(crossing = 60f, count = 2)
        assertEquals(0f, vertical.latitudes().min(), 0.1f)
        assertEquals(90f, vertical.latitudes().max(), 0.5f)
        assertTrue(vertical.points.any { abs(sphere.latitude(it.u) - 45f) < 0.1f })
        val overhead = ideal.overheadEights(axis = 60f, count = 2)
        assertEquals(45f, overhead.latitudes().min(), 0.1f)
        assertEquals(90f, overhead.latitudes().max(), 0.5f)
        val squareEights = ideal.squareEights(crossing = 60f, count = 2)
        assertEquals(2, squareEights.repeats)
        assertEquals(45f, squareEights.latitudes().max(), 0.1f)
        assertEquals(0f, squareEights.latitudes().min(), 0.1f)
        assertEquals(60f - 45f, squareEights.points.minOf { sphere.azimuth(it.u) }, 0.5f)
        assertEquals(60f + 45f, squareEights.points.maxOf { sphere.azimuth(it.u) }, 0.5f)
    }

    @Test
    fun theCloverHasFourEqualLeavesAndEndsAtTheZenith() {
        val track = ideal.clover(climb = 270f)
        assertEquals(4, track.repeats)
        val end = track.points.last().u
        assertEquals(90f, sphere.latitude(end), 0.1f)
        val sizes = (0 until 4).map { r -> track.repeat(r).filter { it.tag == Tag.ARC }.size }
        assertTrue("leaf sizes $sizes", sizes.max() - sizes.min() <= 1)
        // the leaves' arcs are about 270 degrees of turn: their ends are a quarter turn apart round the zenith
        val firstArc = track.repeat(0).filter { it.tag == Tag.ARC }
        assertEquals(90f, abs(wrapDeg(sphere.azimuth(firstArc.last().u) - sphere.azimuth(firstArc.first().u))), 2f)
    }

    @Test
    fun noTrackHasJumps() {
        val tracks = listOf(
            ideal.takeoff(0f), ideal.reverseWingover(0f), ideal.loops(0f, 3), ideal.invertedLaps(0f), ideal.loops(0f, 3, true),
            ideal.squares(0f, 2), ideal.squares(0f, 2, true), ideal.triangles(0f, 2), ideal.horizontalEights(0f, 2),
            ideal.squareEights(0f, 2), ideal.verticalEights(0f, 2), ideal.hourglass(0f), ideal.overheadEights(0f, 2),
            ideal.clover(0f), ideal.landing(0f),
        )
        for ((n, track) in tracks.withIndex()) {
            val worst = track.points.zipWithNext().maxOf { (a, b) -> sphere.angle(a.u, b.u) }
            assertTrue("track $n has a ${worst} degree jump", worst < 2f)
            assertEquals("track $n progress ends at 1", 1f, track.points.last().progress, 1e-4f)
        }
    }
}
