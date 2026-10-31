/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireShapeTest {
    private val shape = WireShape(Wire(0.0005f))
    private val points = List(WIRE_SEGMENTS + 1) { Vector3() }
    private val handle = Vector3(0f, 1.2f, 0f)
    private val heading = Vector3(0f, 0f, -1f)

    private fun straightAt(i: Int, to: Vector3) = Vector3(handle).lerp(to, i / WIRE_SEGMENTS.toFloat())

    @Test
    fun aTautLineIsNearlyStraightAndBowedBackAgainstTheFlight() {
        val plane = Vector3(15f, 1.2f, 0f)
        shape.points(handle, plane, 15f, 20f, 20f, heading, points)
        for (i in points.indices) assertTrue("point $i", points[i].dst(straightAt(i, plane)) < 0.1f)
        val mid = Vector3(points[WIRE_SEGMENTS / 2]).sub(straightAt(WIRE_SEGMENTS / 2, plane))
        assertTrue("mid $mid is not behind the flight", mid.dot(heading) < 0f)
        assertEquals(0f, points.first().dst(handle), 1e-5f)
        assertEquals(0f, points.last().dst(plane), 1e-5f)
    }

    @Test
    fun aSlackLineHangsBelowItsChord() {
        val plane = Vector3(14.95f, 1.2f, 0f)
        shape.points(handle, plane, 15f, 0f, 0f, heading, points)
        val mid = points[WIRE_SEGMENTS / 2]
        val chordMid = straightAt(WIRE_SEGMENTS / 2, plane)
        assertTrue("sag ${chordMid.y - mid.y}", chordMid.y - mid.y > 0.3f)
        // the parabola's sag for 5 cm of slack on 15 m: sqrt(3 * 15 * 0.05 / 8)
        assertEquals(0.53f, chordMid.y - mid.y, 0.05f)
    }

    @Test
    fun theLessTheTensionTheDeeperTheSag() {
        val plane = Vector3(14.9f, 1.2f, 0f)
        shape.points(handle, plane, 15f, 5f, 0f, heading, points)
        val tight = points[WIRE_SEGMENTS / 2].y
        shape.points(handle, plane, 15f, 0.5f, 0f, heading, points)
        assertTrue(points[WIRE_SEGMENTS / 2].y < tight)
    }
}
