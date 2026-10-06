/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.track

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SphereTrackTest {
    private val track = SphereTrack()
    private val handle = Vector3(0f, 1.5f, 0f)

    /** Nose along -Z, left wing towards the pilot (-X): level flight at azimuth 0, anticlockwise. */
    private val levelAtZero = Quaternion(Vector3.Y, 180f)

    private fun sample(position: Vector3, velocity: Vector3, rotation: Quaternion) =
        track.sample(1f, handle, position, velocity, rotation, airborne = true)

    @Test
    fun levelFlight() {
        val s = sample(Vector3(15f, 1.5f, 0f), Vector3(0f, 0f, -20f), levelAtZero)
        assertEquals(0f, s.azimuth, 1e-3f)
        assertEquals(0f, s.elevation, 1e-3f)
        assertEquals(0f, s.climb, 1e-3f)
        assertFalse(s.inverted)
    }

    @Test
    fun aQuarterLapOnTheAzimuthGrowsByNinety() {
        // at -Z the circle goes on towards -X
        val s = sample(Vector3(0f, 1.5f, -15f), Vector3(-20f, 0f, 0f), Quaternion(Vector3.Y, -90f))
        assertEquals(90f, s.azimuth, 1e-3f)
        assertEquals(0f, s.climb, 1e-3f)
        assertFalse(s.inverted)
    }

    @Test
    fun straightUp() {
        val noseUp = Quaternion(levelAtZero).mul(Quaternion(Vector3.X, -90f))
        val s = sample(Vector3(15f, 1.5f, 0f), Vector3(0f, 20f, 0f), noseUp)
        assertEquals(90f, s.climb, 1e-3f)
    }

    @Test
    fun climbingAtFortyFiveDegrees() {
        val s = sample(Vector3(15f, 1.5f, 0f), Vector3(0f, 10f, -10f), levelAtZero)
        assertEquals(45f, s.climb, 1e-3f)
    }

    @Test
    fun overhead() {
        val s = sample(Vector3(0f, 16.5f, 0f), Vector3(0f, 0f, -20f), Quaternion())
        assertEquals(90f, s.elevation, 1e-3f)
    }

    @Test
    fun highOnTheSphere() {
        // 60 degrees up at azimuth 0, flying along the circle
        val s = sample(Vector3(7.5f, 1.5f + 12.990381f, 0f), Vector3(0f, 0f, -20f), levelAtZero)
        assertEquals(60f, s.elevation, 1e-3f)
        assertEquals(0f, s.climb, 1e-3f)
    }

    @Test
    fun upsideDown() {
        val inverted = Quaternion(levelAtZero).mul(Quaternion(Vector3.Z, 180f))
        val s = sample(Vector3(15f, 1.5f, 0f), Vector3(0f, 0f, -20f), inverted)
        assertTrue(s.inverted)
        assertEquals(0f, s.climb, 1e-3f)
    }

    @Test
    fun flyingTheOtherWay() {
        val s = sample(Vector3(15f, 1.5f, 0f), Vector3(0f, 0f, 20f), Quaternion())
        assertEquals(180f, kotlin.math.abs(s.climb), 1e-3f)
    }
}
