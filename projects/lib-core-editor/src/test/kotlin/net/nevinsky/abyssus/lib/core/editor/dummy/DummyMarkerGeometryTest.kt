/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.dummy

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DummyMarkerGeometryTest {
    private val geometry = DummyMarkerGeometry()

    private fun assertVec(expected: Vector3, actual: Vector3) =
        assertTrue("expected $expected, got $actual", expected.epsilonEquals(actual, 1e-4f))

    @Test
    fun threeAxesOfTheGivenLength() {
        val lines = geometry.lines(PlacementTransform(Vector3(1f, 2f, 3f), Quaternion(), Vector3(5f, 5f, 5f)), 2f)
        assertEquals(listOf(0, 1, 2), lines.map { it.axis })
        assertVec(Vector3(3f, 2f, 3f), lines[0].to)
        assertVec(Vector3(1f, 4f, 3f), lines[1].to)
        assertVec(Vector3(1f, 2f, 5f), lines[2].to)
        lines.forEach { assertVec(Vector3(1f, 2f, 3f), it.from) }
    }

    @Test
    fun aQuarterTurnAboutYPointsTheRedLineAlongMinusZ() {
        val lines = geometry.lines(PlacementTransform(Vector3(), Quaternion(Vector3.Y, 90f), Vector3(1f, 1f, 1f)), 1f)
        assertVec(Vector3(0f, 0f, -1f), lines[0].to)
    }

    @Test
    fun theLengthIsFixedOnScreen() {
        // at 10 units with a 90 degree field of view, 20 units are visible over 200 pixels: 0.1 per pixel
        assertEquals(2.4f, geometry.worldLength(10f, 90f, 200f, 24f), 1e-4f)
        assertEquals(4.8f, geometry.worldLength(20f, 90f, 200f, 24f), 1e-4f)
    }

    @Test
    fun aClickNearTheCentrePicks() {
        assertTrue(geometry.picks(100f, 100f, 104f, 105f))
        assertFalse(geometry.picks(100f, 100f, 120f, 100f))
    }
}
