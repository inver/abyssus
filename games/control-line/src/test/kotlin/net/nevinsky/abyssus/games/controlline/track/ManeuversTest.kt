/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.track

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Synthetic flight paths, sampled at 120 Hz like the physics steps. */
class ManeuversTest {
    private class Path {
        val samples = ArrayList<TrackSample>()
        var time = 0f
        var azimuth = 0f

        /** [seconds] of flight with each value a function of the fraction done (0..1). */
        fun fly(seconds: Float, elevation: (Float) -> Float, climb: (Float) -> Float, inverted: Boolean = false, turn: Float = 360f / 5f) {
            val steps = Math.round(seconds * 120)
            for (i in 1..steps) {
                val f = i.toFloat() / steps
                time += 1f / 120f
                azimuth = wrapDegrees(azimuth + turn / 120f)
                samples += TrackSample(time, azimuth, elevation(f), wrapDegrees(climb(f)), inverted, airborne = true)
            }
        }

        fun level(seconds: Float, elevation: Float = 10f) = fly(seconds, { elevation }, { 0f })

        /** Climb angle turns through [degrees] in [seconds], over a top at 40 degrees of elevation, back at 10. */
        fun loop(degrees: Float, seconds: Float = 5f) =
            fly(seconds, { 10f + 30f * sin(PI.toFloat() * it) }, { degrees * it })
    }

    private fun detect(path: Path): List<Maneuver> {
        val maneuvers = Maneuvers()
        return path.samples.flatMap { maneuvers.sample(it) }.map { it.maneuver } + maneuvers.finish().map { it.maneuver }
    }

    @Test
    fun insideLoop() {
        val path = Path().apply {
            level(1f)
            loop(360f)
            level(3f)
        }
        assertEquals(listOf(Maneuver.INSIDE_LOOP), detect(path))
    }

    @Test
    fun anInsideLoopScoresAtItsEndTime() {
        val path = Path().apply {
            level(1f)
            loop(360f)
            level(3f)
        }
        val maneuvers = Maneuvers()
        val events = path.samples.flatMap { maneuvers.sample(it) }
        // when the climb angle is back within 20 degrees of level: 340 of the 360 degrees, 5 s from 1 s
        assertEquals(1f + 5f * 340f / 360f, events.single().time, 0.02f)
    }

    @Test
    fun aLoopTooSlowIsNotALoop() {
        val path = Path().apply {
            level(1f)
            loop(360f, seconds = 9f)
            level(3f)
        }
        assertEquals(emptyList<Maneuver>(), detect(path))
    }

    @Test
    fun halfALoopIsNotALoop() {
        // up through vertical, then the nose comes back down the way it went up
        val path = Path().apply {
            level(1f)
            fly(1.5f, { 10f + 25f * it }, { 110f * it })
            fly(1.5f, { 35f - 10f * it }, { 110f - 200f * it })
            fly(1f, { 25f - 15f * it }, { -90f + 90f * it })
            level(3f)
        }
        assertEquals(emptyList<Maneuver>(), detect(path))
    }

    @Test
    fun halfALoopTurningBackIsNotALoop() {
        // over the top and down, then pulled out flying the other way round
        val path = Path().apply {
            level(1f)
            fly(3f, { 10f + 30f * sin(PI.toFloat() * it) }, { 270f * it })
            fly(1f, { 10f }, { 270f - 90f * it })
            level(3f)
        }
        assertEquals(emptyList<Maneuver>(), detect(path))
    }

    @Test
    fun outsideLoop() {
        val path = Path().apply {
            level(1f)
            loop(-360f)
            level(3f)
        }
        assertEquals(listOf(Maneuver.OUTSIDE_LOOP), detect(path))
    }

    @Test
    fun figureEightReplacesItsLoops() {
        val path = Path().apply {
            level(1f)
            loop(360f)
            level(1f)
            loop(-360f)
            level(3f)
        }
        assertEquals(listOf(Maneuver.FIGURE_EIGHT), detect(path))
    }

    @Test
    fun loopsTooFarApartAreNoFigureEight() {
        val path = Path().apply {
            level(1f)
            loop(360f)
            level(3f)
            loop(-360f)
            level(3f)
        }
        assertEquals(listOf(Maneuver.INSIDE_LOOP, Maneuver.OUTSIDE_LOOP), detect(path))
    }

    @Test
    fun aHeldLoopScoresWhenTheFlightEnds() {
        val path = Path().apply {
            level(1f)
            loop(360f)
            level(0.5f)
        }
        assertEquals(listOf(Maneuver.INSIDE_LOOP), detect(path))
    }

    @Test
    fun invertedLap() {
        val path = Path().apply {
            level(1f)
            fly(5.2f, { 20f }, { 0f }, inverted = true)
            level(1f)
        }
        assertEquals(listOf(Maneuver.INVERTED_LAP), detect(path))
    }

    @Test
    fun aShortInvertedPassIsNoLap() {
        val path = Path().apply {
            level(1f)
            fly(3f, { 20f }, { 0f }, inverted = true)
            level(1f)
        }
        assertEquals(emptyList<Maneuver>(), detect(path))
    }

    @Test
    fun invertedHighUpIsNoInvertedLap() {
        val path = Path().apply {
            level(1f)
            fly(6f, { 50f }, { 0f }, inverted = true)
            level(1f)
        }
        assertEquals(emptyList<Maneuver>(), detect(path))
    }

    @Test
    fun wingover() {
        val path = Path().apply {
            level(1f, elevation = 5f)
            fly(2f, { 5f + 84f * it }, { 90f }, turn = 0f)
            azimuth = wrapDegrees(azimuth + 180f)
            fly(2f, { 89f - 79f * it }, { -90f }, turn = 0f)
            fly(0.5f, { 10f - 5f * it }, { -90f + 90f * it })
            level(2f, elevation = 5f)
        }
        assertEquals(listOf(Maneuver.WINGOVER), detect(path))
    }

    @Test
    fun aClimbThatComesBackIsNoWingover() {
        val path = Path().apply {
            level(1f, elevation = 5f)
            fly(2f, { 5f + 80f * it }, { 90f }, turn = 0f)
            fly(2f, { 85f - 80f * it }, { -90f }, turn = 0f)
            fly(0.5f, { 5f }, { -90f - 90f * it })
            level(2f, elevation = 5f)
        }
        assertEquals(emptyList<Maneuver>(), detect(path))
    }

    @Test
    fun lapsCountFullTurnsInTheAir() {
        val laps = Laps()
        val path = Path().apply { level(15.1f) }
        path.samples.forEach { laps.sample(it) }
        assertEquals(3, laps.count)
    }

    @Test
    fun noLapsOnTheGround() {
        val laps = Laps()
        val path = Path().apply { level(15.1f) }
        path.samples.forEach { laps.sample(it.copy(airborne = false)) }
        assertEquals(0, laps.count)
    }
}
