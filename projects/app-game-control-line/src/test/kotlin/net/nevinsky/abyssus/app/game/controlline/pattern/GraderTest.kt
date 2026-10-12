/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraderTest {
    private val ideal = IdealTracks(18f)
    private val matchers = Matchers(18f)
    private val grader = Grader(18f)

    private fun gradeOf(track: IdealTrack, perturbation: Perturbation = Perturbation()): Graded {
        val flight = BurstFlight(track, perturbation)
        val recognition = matchers.recognise(flight.burst, flight.path.points)
        assertNotNull("nothing recognised", recognition)
        return grader.grade(recognition!!, flight.path.points, flight.burst)
    }

    private fun Graded.describe() = "$mark ${deductions.joinToString { "${it.criterion} ${it.measured}→${it.amount}" }}"

    private val figures: List<Pair<String, IdealTrack>> = listOf(
        "reverse wingover" to ideal.reverseWingover(30f),
        "inside loops" to ideal.loops(100f, 3),
        "outside loops" to ideal.loops(100f, 3, inverted = true),
        "inside squares" to ideal.squares(80f, 2),
        "outside squares" to ideal.squares(80f, 2, inverted = true),
        "triangles" to ideal.triangles(100f, 2),
        "horizontal eights" to ideal.horizontalEights(60f, 2, connected = true),
        "square eights" to ideal.squareEights(60f, 2, connected = true),
        "vertical eights" to ideal.verticalEights(60f, 2),
        "hourglass" to ideal.hourglass(150f),
        "overhead eights" to ideal.overheadEights(60f, 2, connected = true),
        "clover" to ideal.clover(270f, connected = true),
    )

    @Test
    fun everyIdealTrackMarksTen() {
        val failures = figures.mapNotNull { (name, track) ->
            val graded = gradeOf(track)
            if (graded.tenths == 100) null else "$name: ${graded.describe()}"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun highBottomsMarkNoHigherAndNeverBelowOne() {
        val marks = listOf(0.8f, 1.3f, 2.3f).map { gradeOf(ideal.loops(100f, 3), Perturbation(lowRaise = it)) }
        assertTrue(marks.zipWithNext().all { (a, b) -> b.tenths <= a.tenths })
        assertTrue(marks.all { it.tenths >= 10 })
        assertTrue("0.5 m over the tolerance still marks 10: ${marks[0].describe()}", marks[2].tenths < 100)
    }

    @Test
    fun squaresAQuarterLapApartMarkLowerThanSuperimposedOnes() {
        val together = gradeOf(ideal.squares(80f, 2))
        val apart = gradeOf(ideal.squares(80f, 2), Perturbation(repeatShift = 90f))
        assertTrue("together ${together.describe()}, apart ${apart.describe()}", apart.tenths < together.tenths)
    }

    @Test
    fun cornersOfFourMetresMarkLowerThanCornersOfOne() {
        val tight = gradeOf(ideal.squares(80f, 2))
        val round = gradeOf(IdealTracks(18f, cornerMetres = 4f).squares(80f, 2))
        assertTrue("tight ${tight.describe()}, round ${round.describe()}", round.tenths < tight.tenths)
    }

    @Test
    fun aBadlyFlownTriangleStillMarksBetweenOneAndTen() {
        val bad = gradeOf(IdealTracks(18f, cornerMetres = 3.5f).triangles(100f, 2, apexLatitude = 60f))
        assertTrue(bad.describe(), bad.tenths in 10..100)
    }

    @Test
    fun aWobblyExitDoesNotChangeTheMark() {
        val clean = BurstFlight(ideal.loops(100f, 3))
        val flightWobbly = SyntheticFlight()
        val sphere = HemisphereGeometry(18f)
        val track = ideal.loops(100f, 3)
        val start = sphere.azimuth(track.points.first().u)
        flightWobbly.level(to = start, from = start - 40f)
        flightWobbly.fly(track)
        // after the loops: a wobble, instead of level flight
        flightWobbly.level(to = flightWobbly.endAzimuth + 30f)
        val wobble = (0..400).map { i -> place(2f * kotlin.math.sin(i / 12f) + 2f, flightWobbly.endAzimuth + i * 0.4f) to false }
        flightWobbly.flyPoints(wobble)
        flightWobbly.level(to = flightWobbly.endAzimuth + 40f)
        val path = PathTokens(18f)
        for (s in flightWobbly.samples) path.feed(s)
        path.flush()
        val pausesRemoved = path.tokens.filter { !(it is Level && it.azimuthSpan >= PAUSE_AZIMUTH) }
        val cleanMark = matchers.recognise(clean.burst, clean.path.points)!!.let { grader.grade(it, clean.path.points, clean.burst) }
        val loops = pausesRemoved.filter { it is Arc }
        val r = matchers.recognise(loops, path.points)!!
        val wobblyMark = grader.grade(r, path.points, loops)
        assertEquals(cleanMark.describe(), cleanMark.tenths, wobblyMark.tenths)
    }

    private fun pointsOf(samples: List<net.nevinsky.abyssus.app.game.controlline.track.TrackSample>): PathTokens {
        val path = PathTokens(18f)
        for (s in samples) path.feed(s)
        path.flush()
        return path
    }

    @Test
    fun theIdealTakeoffMarksTen() {
        val flight = SyntheticFlight()
        flight.fly(ideal.takeoff(release = 20f, roll = 8f))
        val path = pointsOf(flight.samples)
        val liftoff = path.points.indexOfFirst { it.height > path.points[0].height + 0.1f }
        val graded = grader.gradeTakeoff(path.points, liftoff, path.points.size - 1)
        assertEquals(graded.describe(), 100, graded.tenths)
    }

    @Test
    fun aTooLongRollMarksLower() {
        val long = SyntheticFlight().also { it.fly(ideal.takeoff(release = 20f, roll = 8f)) }
        fun mark(f: SyntheticFlight): Graded {
            val path = pointsOf(f.samples)
            return grader.gradeTakeoff(path.points, path.points.indexOfFirst { it.height > path.points[0].height + 0.1f }, path.points.size - 1)
        }
        val tooLong = SyntheticFlight().also { it.fly(ideal.takeoff(release = 20f, roll = 40f)) }
        assertTrue(mark(tooLong).tenths < mark(long).tenths)
    }

    private fun glide(laps: Float): Pair<PathTokens, Int> {
        val flight = SyntheticFlight()
        flight.level(to = 100f, from = 60f)
        val ground = ideal.groundLatitude
        val n = 360
        flight.flyPoints(List(n + 1) { place(ground * it / n, 100f + 360f * laps * it / n) to false })
        val path = pointsOf(flight.samples)
        return path to path.points.indexOfLast { it.height >= 1.5f - 0.3f }
    }

    @Test
    fun aHalfLapGlideMarksLowerThanALapGlide() {
        val (lap, lapFrom) = glide(1f)
        val (half, halfFrom) = glide(0.5f)
        val a = grader.gradeLanding(lap.points, lapFrom, lap.points.size - 1, 0.5f)
        val b = grader.gradeLanding(half.points, halfFrom, half.points.size - 1, 0.5f)
        assertEquals(a.describe(), 100, a.tenths)
        assertTrue("lap ${a.describe()}, half ${b.describe()}", b.tenths < a.tenths)
    }

    @Test
    fun aHardTouchdownMarksLower() {
        val (lap, from) = glide(1f)
        val soft = grader.gradeLanding(lap.points, from, lap.points.size - 1, 0.5f)
        val hard = grader.gradeLanding(lap.points, from, lap.points.size - 1, 2.8f)
        assertTrue(hard.tenths < soft.tenths)
    }
}
