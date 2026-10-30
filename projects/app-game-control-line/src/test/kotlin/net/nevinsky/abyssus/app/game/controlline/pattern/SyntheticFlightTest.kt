/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticFlightTest {
    private val sphere = HemisphereGeometry(18f)
    private val ideal = IdealTracks(18f)

    @Test
    fun anIdealLoopRoundTripsToTheSameDirections() {
        val track = ideal.loops(vertical = 40f, count = 1)
        val flown = SyntheticFlight().fly(track)
        assertTrue(flown.size > 100)
        for (s in flown) {
            val u = sphere.direction(s)
            assertTrue("a sample is ${track.points.minOf { sphere.angle(it.u, u) }} degrees off the ideal", track.points.any { sphere.angle(it.u, u) < 0.3f })
        }
        // one step of 1/120 s at 22 m/s is 0.18 m
        val step = sphere.distance(sphere.direction(flown[10]), sphere.direction(flown[11]))
        assertEquals(22f / 120f, step, 0.01f)
        assertEquals(1f / 120f, flown[11].time - flown[10].time, 1e-6f)
    }

    @Test
    fun theHeightIsTheHeightAboveTheGround() {
        val flown = SyntheticFlight().fly(ideal.loops(vertical = 40f, count = 1))
        assertEquals(1.5f, flown.first().height, 0.05f)
        assertEquals(1.5f + 18f * Math.sin(Math.toRadians(45.0)).toFloat(), flown.maxOf { it.height }, 0.2f)
        assertTrue(flown.all { it.airborne })
    }

    @Test
    fun anInsideLoopLeansTheWayItTurnsAndAnInvertedOneTheOtherWay() {
        val upright = SyntheticFlight().fly(ideal.loops(vertical = 40f, count = 1))
        val inverted = SyntheticFlight().fly(ideal.loops(vertical = 40f, count = 1, inverted = true))
        assertTrue(upright.first().planeUp.y > 0.9f && !upright.first().inverted)
        assertTrue(inverted.first().planeUp.y < -0.9f && inverted.first().inverted)
    }

    @Test
    fun perturbationsMoveThePath() {
        val track = ideal.loops(vertical = 40f, count = 2)
        val plain = SyntheticFlight().fly(track)
        val shifted = SyntheticFlight().fly(track, Perturbation(azimuthShift = 10f))
        assertEquals(10f, sphere.azimuth(sphere.direction(shifted.first())) - sphere.azimuth(sphere.direction(plain.first())), 0.1f)
        val raised = SyntheticFlight().fly(track, Perturbation(lowRaise = 1f))
        assertEquals(plain.first().height + 1f, raised.first().height, 0.1f)
        val apart = SyntheticFlight().fly(track, Perturbation(repeatShift = 90f))
        assertTrue(apart.last().offset.dst(plain.last().offset) > 10f)
    }
}
