/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathCurveTest {
    private val curve = PathCurve()

    private val bend = listOf(Vector3(0f, 0f, 0f), Vector3(10f, 0f, 0f), Vector3(10f, 0f, 10f))

    @Test
    fun twoPointsStayAStraightLine() {
        val a = Vector3(0f, 0f, 0f)
        val b = Vector3(20f, 0f, 0f)
        assertEquals(listOf(a, b), curve.sample(listOf(a, b), closed = false))
    }

    @Test
    fun theCurvePassesThroughEveryControlPoint() {
        val line = curve.sample(bend, closed = false)
        for (p in bend) assertTrue("misses $p", line.any { kotlin.math.hypot(it.x - p.x, it.z - p.z) < 1e-3f })
        assertEquals(bend.last(), line.last())
    }

    @Test
    fun aCornerIsRounded() {
        // a straight chain would keep the middle of the second span on x = 10
        val mid = curve.at(bend, closed = false, span = 1, t = 0.5f)
        assertTrue("x=${mid.x}", mid.x > 10f && mid.x < 12f) // the heading into the corner carries the curve past it
        assertEquals(5f, mid.z, 1f)
    }

    @Test
    fun aClosedLoopEndsOnItsFirstPoint() {
        val line = curve.sample(bend, closed = true)
        assertEquals(bend.first(), line.last())
        assertEquals(3 * PATH_CURVE_STEPS + 1, line.size)
    }
}
