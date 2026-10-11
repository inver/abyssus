/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import org.junit.Assert.assertEquals
import org.junit.Test

class HemisphereGeometryTest {
    private val sphere = HemisphereGeometry(18f)

    private fun sampleAt(offset: Vector3) = TrackSample(0f, 0f, 0f, 0f, false, true, offset)

    @Test
    fun theBaseIsLatitudeZeroAndTheParallelFortyFive() {
        assertEquals(0f, sphere.latitude(sphere.direction(0f, 30f)), 1e-4f)
        assertEquals(45f, sphere.latitude(sphere.direction(45f, 100f)), 1e-3f)
        // 18 m out at the base: the plane is 1.5 m up; at 45 degrees it is 12.73 m higher
        val u = sphere.direction(sampleAt(Vector3(18f, 1.5f, 0f)))
        assertEquals(0f, sphere.latitude(u), 1e-4f)
        val v = sphere.direction(sampleAt(Vector3(18f * 0.70710678f, 1.5f + 18f * 0.70710678f, 0f)))
        assertEquals(45f, sphere.latitude(v), 1e-3f)
    }

    @Test
    fun theZenithIsNinety() {
        val u = sphere.direction(sampleAt(Vector3(0f, 19.5f, 0f)))
        assertEquals(90f, sphere.latitude(u), 1e-3f)
        assertEquals(19.5f, sphere.position(u).y, 1e-3f)
    }

    @Test
    fun anEighthOfALapAtTheBaseIsFortyFiveDegreesOfAzimuth() {
        val a = sphere.direction(0f, 10f)
        val b = sphere.direction(0f, 55f)
        assertEquals(45f, sphere.angle(a, b), 1e-3f)
        assertEquals(sphere.lapsToMetres(0.125f), sphere.arc(45f), 1e-3f)
        assertEquals(0.125f, sphere.azimuthLaps(b) - sphere.azimuthLaps(a), 1e-4f)
        assertEquals(18f * Math.PI.toFloat() / 4f, sphere.arc(45f), 1e-3f)
    }

    @Test
    fun aSmallCircleOfTwentyTwoAndAHalfDegreesTouchesTheBaseAndTheParallel() {
        val center = sphere.direction(22.5f, 70f)
        var lowest = 90f
        var highest = -90f
        for (theta in 0 until 360 step 5) {
            val p = sphere.smallCircleFromBottom(center, 22.5f, theta.toFloat())
            assertEquals(22.5f, sphere.angle(center, p), 1e-2f)
            lowest = minOf(lowest, sphere.latitude(p))
            highest = maxOf(highest, sphere.latitude(p))
        }
        assertEquals(0f, lowest, 0.05f)
        assertEquals(45f, highest, 0.05f)
        // phase 0 is the bottom and the circle runs the way the azimuth grows
        val bottom = sphere.smallCircleFromBottom(center, 22.5f, 0f)
        val next = sphere.smallCircleFromBottom(center, 22.5f, 10f)
        assertEquals(0f, sphere.latitude(bottom), 0.05f)
        assertEquals(true, sphere.azimuth(next) > sphere.azimuth(bottom))
    }

    @Test
    fun greatCirclesAndDistances() {
        val a = sphere.direction(0f, 0f)
        val b = sphere.direction(90f, 0f)
        val mid = sphere.greatCircle(a, b, 0.5f)
        assertEquals(45f, sphere.latitude(mid), 1e-3f)
        assertEquals(0f, sphere.offGreatCircle(mid, a, b), 1e-3f)
        assertEquals(18f * Math.PI.toFloat() / 2f, sphere.arc(90f), 1e-3f)
        assertEquals(18f * 1.4142135f, sphere.distance(a, b), 1e-3f)
        assertEquals(10f, sphere.offGreatCircle(sphere.direction(10f, 0f), a, sphere.direction(0f, 90f)), 1e-3f)
    }
}
