/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidePlannerTest {
    private val radius = 18f
    private val sphere = HemisphereGeometry(radius)

    private fun planner() = GuidePlanner(radius)

    private fun at(azimuth: Float) = place(0f, azimuth)

    private fun GuidePlanner.input(
        expected: Figure?, pause: Float? = PAUSE_LAPS, azimuth: Float = 0f, started: Boolean = false, engine: Boolean = true,
    ) = update(GuideInput(expected, pause, started, at(azimuth), engine))

    private fun GuidePlanner.track() = segments().filter { it.tag == GuideTag.GUIDE_AHEAD || it.tag == GuideTag.GUIDE_DONE }

    private fun GuidePlanner.count(tag: GuideTag) = segments().count { it.tag == tag }

    private fun gate(planner: GuidePlanner) = planner.segments().single { it.tag == GuideTag.GATE }

    @Test
    fun theRingsAreThereFromTheStart() {
        val planner = planner()
        assertEquals(128, planner.count(GuideTag.RING_BASE))
        assertEquals(128, planner.count(GuideTag.RING_45))
        assertEquals(0, planner.track().size)
        planner.update(GuideInput(Figure.TAKEOFF, null, false, null, true))
        assertNull(planner.figure)
        planner.input(Figure.TAKEOFF, pause = null)
        assertEquals(Figure.TAKEOFF, planner.figure)
        assertEquals(256, planner.segments().size - planner.track().size)
        assertNull(planner.gateAzimuth)
        // the ring at the base is at the base's height, and the other is at the 45° parallel
        assertEquals(BASE_HEIGHT, planner.segments().first { it.tag == GuideTag.RING_BASE }.from.y, 1e-3f)
        assertEquals(BASE_HEIGHT + radius * kotlin.math.sin(Math.toRadians(45.0)).toFloat(), planner.segments().first { it.tag == GuideTag.RING_45 }.from.y, 1e-2f)
    }

    @Test
    fun theTakeoffGuideStartsOnTheGroundAtTheReleasePoint() {
        val planner = planner()
        planner.input(Figure.TAKEOFF, pause = null, azimuth = 40f)
        val first = planner.track().first().from
        assertEquals(40f, sphere.azimuth(Vector3(first).sub(0f, BASE_HEIGHT, 0f).nor()), 0.5f)
        assertTrue(first.y < 0.3f)
    }

    @Test
    fun everyManoeuvresGuideStartsAtItsGate() {
        for (here in listOf(0f, 33f, -170f, 175f)) for (figure in SCHEDULE.drop(1)) {
            val planner = planner()
            planner.input(figure, azimuth = here, engine = figure != Figure.LANDING)
            assertEquals(figure.label, figure, planner.figure)
            val expectedGate = wrapAzimuth(here + 90f)
            assertEquals(figure.label, expectedGate, planner.gateAzimuth!!, 1e-3f)
            val bar = gate(planner)
            assertEquals(0f, bar.from.y, 0f)
            assertEquals(BASE_HEIGHT + GATE_HEIGHT, bar.to.y, 1e-4f)
            val start = planner.track().first().from
            val gateBase = Vector3(bar.from.x, BASE_HEIGHT, bar.from.z)
            assertTrue("${figure.label}: ${start.dst(gateBase)} m", start.dst(gateBase) < 0.1f)
        }
    }

    @Test
    fun nothingIsDrawnBeforeThePauseIsDoneOrOnceTheMuvreHasStarted() {
        val planner = planner()
        planner.input(Figure.INSIDE_LOOPS, pause = PAUSE_LAPS - 0.1f)
        assertNull(planner.figure)
        planner.input(Figure.INSIDE_LOOPS, pause = null)
        assertNull(planner.figure)
        planner.input(Figure.INSIDE_LOOPS, pause = PAUSE_LAPS, started = true)
        assertNull(planner.figure)
        planner.input(Figure.INSIDE_LOOPS, pause = PAUSE_LAPS)
        assertEquals(Figure.INSIDE_LOOPS, planner.figure)
    }

    @Test
    fun theGuideStaysWherePlacedAndWaitsForThePilot() {
        val planner = planner()
        planner.input(Figure.INSIDE_SQUARES, azimuth = 10f)
        val before = planner.segments().map { it.from.x }
        // the plane flies a lap and a half in level flight, past the gate
        for (az in 11..550 step 3) planner.input(Figure.INSIDE_SQUARES, pause = 2f + az / 360f, azimuth = az.toFloat())
        assertEquals(Figure.INSIDE_SQUARES, planner.figure)
        assertEquals(100f, planner.gateAzimuth!!, 1e-3f)
        assertEquals(before, planner.segments().map { it.from.x })
        assertEquals(0, planner.passed)
    }

    @Test
    fun aLaterManoeuvreScoredRemovesTheGuide() {
        val planner = planner()
        planner.input(Figure.INVERTED_LAPS)
        assertEquals(Figure.INVERTED_LAPS, planner.figure)
        // the pilot flew the outside loops and more: the judge skips on to the squares
        planner.input(Figure.INSIDE_SQUARES, pause = 0.2f)
        assertNull(planner.figure)
        assertEquals(0, planner.track().size)
        planner.input(Figure.INSIDE_SQUARES, pause = PAUSE_LAPS)
        assertEquals(Figure.INSIDE_SQUARES, planner.figure)
    }

    @Test
    fun theLandingAppearsOnlyOnceTheEngineHasStopped() {
        val planner = planner()
        planner.input(Figure.LANDING, pause = 3f, engine = true)
        assertNull(planner.figure)
        planner.input(Figure.LANDING, pause = 3f, engine = false, azimuth = 20f)
        assertEquals(Figure.LANDING, planner.figure)
        assertEquals(110f, planner.gateAzimuth!!, 1e-3f)
        // one lap, from the base down to the ground
        val track = planner.track()
        assertTrue(track.first().from.y > BASE_HEIGHT - 0.1f)
        assertTrue(track.last().to.y < 0.3f)
        planner.input(null, pause = null)
        assertNull(planner.figure)
    }

    /** Moves the plane along [directions], the way a flight does, with the manoeuvre started. */
    private fun GuidePlanner.fly(figure: Figure, directions: List<Vector3>) {
        for (u in directions) update(GuideInput(figure, PAUSE_LAPS, true, u, true))
    }

    /** The loops' track as the planner places it for a plane at azimuth 0. */
    private fun placedLoops(): List<Vector3> {
        val points = IdealTracks(radius).loops(0f, 3).points
        val turn = wrapAzimuth(90f - sphere.azimuth(points.first().u))
        return points.map { Vector3(it.u).rotate(Vector3.Y, turn) }
    }

    @Test
    fun halfwayThroughTheLoopsTheFlownPartIsDimmed() {
        val planner = planner()
        planner.input(Figure.INSIDE_LOOPS)
        val path = placedLoops()
        assertEquals(0, planner.count(GuideTag.GUIDE_DONE))
        // to the top of the second loop
        val top = path.size / 2
        planner.fly(Figure.INSIDE_LOOPS, path.subList(0, top + 1))
        assertEquals(top, planner.passed)
        assertEquals(top, planner.count(GuideTag.GUIDE_DONE))
        assertEquals(path.size - 1 - top, planner.count(GuideTag.GUIDE_AHEAD))
        assertEquals(0.5f, top / (path.size - 1f), 0.02f)
    }

    @Test
    fun progressNeverGoesBackWhileThePlanePassesPointsOfAnEarlierRepeat() {
        val planner = planner()
        planner.input(Figure.INSIDE_LOOPS)
        val path = placedLoops()
        var last = 0
        for (u in path) {
            planner.fly(Figure.INSIDE_LOOPS, listOf(u))
            assertTrue(planner.passed >= last)
            last = planner.passed
        }
        assertTrue(last > path.size - 40)
        // back at the bottom, where all three loops meet: no step back
        planner.fly(Figure.INSIDE_LOOPS, listOf(path.first()))
        assertTrue(planner.passed >= last)
        // and a plane that has not moved up the track yet stays at its start, not on a later loop at the same place
        val fresh = planner()
        fresh.input(Figure.INSIDE_LOOPS)
        fresh.fly(Figure.INSIDE_LOOPS, listOf(path.first(), path.first()))
        assertTrue(fresh.passed < 5)
    }

    @Test
    fun aGuideNotStartedStaysBright() {
        val planner = planner()
        planner.input(Figure.INSIDE_LOOPS)
        for (u in placedLoops().take(100)) planner.update(GuideInput(Figure.INSIDE_LOOPS, PAUSE_LAPS, false, u, true))
        assertEquals(0, planner.count(GuideTag.GUIDE_DONE))
    }

    // the judge's side of it

    @Test
    fun loopsAppearAQuarterLapAheadAfterTheWingoverAndThePauseOfOneAndAHalfLaps() {
        val schedule = SyntheticSchedule(radius)
        val planner = planner()
        val seen = ArrayList<Pair<Figure, Float>>() // the figure drawn when it first appears, and the plane's azimuth then
        var appeared: Float? = null
        schedule.afterSample = {
            planner.update(schedule.judge)
            val figure = planner.figure
            if (figure != null && seen.none { it.first == figure }) {
                seen += figure to sphere.azimuth(checkNotNull(schedule.judge.guideInput().direction))
                if (figure == Figure.INSIDE_LOOPS) assertTrue(schedule.judge.pauseLaps!! in PAUSE_LAPS..PAUSE_LAPS + 0.01f)
                assertEquals(false, schedule.judge.started)
            }
            if (figure == Figure.INSIDE_LOOPS && appeared == null) appeared = planner.gateAzimuth
        }
        schedule.takeoff()
        schedule.figure { schedule.tracks.reverseWingover(it) }
        schedule.figure { schedule.tracks.loops(it, 3) }
        schedule.pause(1f)
        assertTrue(seen.map { it.first }.containsAll(listOf(Figure.TAKEOFF, Figure.REVERSE_WINGOVER, Figure.INSIDE_LOOPS)))
        val (_, plane) = seen.first { it.first == Figure.INSIDE_LOOPS }
        assertEquals(wrapAzimuth(plane + 90f), checkNotNull(appeared), 1e-2f)
        // the pilot flew the loops where the guide was not, and they were scored: the guide is gone and the next is waiting
        assertNotNull(schedule.judge.sheet[Figure.INSIDE_LOOPS])
        assertFalse(planner.figure == Figure.INSIDE_LOOPS)
    }

    @Test
    fun nothingIsDrawnBetweenTheCloverAndTheStoppedEngine() {
        val schedule = SyntheticSchedule(radius)
        val planner = planner()
        val drawn = HashSet<Figure>()
        schedule.afterSample = {
            planner.update(schedule.judge)
            planner.figure?.let(drawn::add)
        }
        schedule.everythingBeforeTheLanding()
        schedule.pause(2f)
        assertEquals(Figure.LANDING, schedule.judge.expected)
        assertNull(planner.figure)
        assertFalse(Figure.LANDING in drawn)
        schedule.landing()
        assertTrue(Figure.LANDING in drawn)
        assertTrue(SCHEDULE.dropLast(1).all { it in drawn })
    }
}
