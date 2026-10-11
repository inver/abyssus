/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathPoint
import net.nevinsky.abyssus.lib.core.ecs.component.PathSides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PathLayoutTest {
    private fun line(
        vararg points: PathPoint,
        closed: Boolean = false,
        spacing: Float = 10f,
        offset: Float = 0f,
        sides: PathSides = PathSides.CENTER,
        align: Boolean = true,
        yaw: Float = 0f,
    ) = PathArrangementComponent().also {
        it.points = points.toMutableList()
        it.closed = closed
        it.spacing = spacing
        it.offset = offset
        it.sides = sides
        it.alignToPath = align
        it.yawOffset = yaw
    }

    private fun p(x: Float, z: Float, y: Float = 0f) = PathPoint(x, y, z)

    private fun slots(c: PathArrangementComponent) = PathLayout().slots(c)!!

    @Test
    fun anOpenFortyUnitLineHasFiveStations() {
        val s = slots(line(p(0f, 0f), p(40f, 0f)))
        assertEquals(listOf(0f, 10f, 20f, 30f, 40f), s.map { it.x })
    }

    @Test
    fun aClosedSquareHasEightStationsAndNoneRepeatsTheFirst() {
        val s = slots(line(p(0f, 0f), p(20f, 0f), p(20f, 20f), p(0f, 20f), closed = true))
        assertEquals(8, s.size)
        assertEquals(listOf(0f to 0f, 10f to 0f, 20f to 0f, 20f to 10f, 20f to 20f, 10f to 20f, 0f to 20f, 0f to 10f),
            s.map { it.x to it.z })
    }

    @Test
    fun aShortLineHasOneStationAtTheFirstPoint() {
        val s = slots(line(p(0f, 0f), p(5f, 0f)))
        assertEquals(listOf(0f), s.map { it.x })
    }

    @Test
    fun bothSidesPutLeftThenRightOfEachStation() {
        val s = slots(line(p(0f, 0f), p(10f, 0f), spacing = 10f, offset = 3f, sides = PathSides.BOTH))
        assertEquals(listOf(0f to -3f, 0f to 3f, 10f to -3f, 10f to 3f), s.map { it.x to it.z })
    }

    @Test
    fun centreIgnoresTheOffset() {
        val s = slots(line(p(0f, 0f), p(10f, 0f), offset = 3f))
        assertEquals(listOf(0f, 0f), s.map { it.z })
    }

    @Test
    fun leftAndRightAreSeenFacingAlongTheLine() {
        assertEquals(-3f, slots(line(p(0f, 0f), p(10f, 0f), offset = 3f, sides = PathSides.LEFT))[0].z, 1e-5f)
        assertEquals(3f, slots(line(p(0f, 0f), p(10f, 0f), offset = 3f, sides = PathSides.RIGHT))[0].z, 1e-5f)
    }

    @Test
    fun aLineAlongPlusZIsAQuarterTurn() {
        assertEquals(-90f, slots(line(p(0f, 0f), p(0f, 20f)))[0].yawDegrees, 1e-4f)
    }

    @Test
    fun withoutAlignmentOnlyTheYawOffsetTurnsACopy() {
        val s = slots(line(p(0f, 0f), p(10f, 0f), p(10f, 20f), align = false, yaw = 90f))
        assertEquals(setOf(90f), s.map { it.yawDegrees }.toSet())
    }

    @Test
    fun rightCopiesOfBothSidesGetAHalfTurn() {
        val s = slots(line(p(0f, 0f), p(10f, 0f), offset = 3f, sides = PathSides.BOTH))
        assertEquals(0f, s[0].yawDegrees, 1e-4f)
        assertEquals(180f, s[1].yawDegrees, 1e-4f)
    }

    @Test
    fun theHeightIsInterpolatedAlongTheLine() {
        val s = slots(line(p(0f, 0f, y = 2f), p(20f, 0f, y = 4f)))
        assertEquals(listOf(2f, 3f, 4f), s.map { it.fallbackY })
    }

    @Test
    fun aStationOnABendTurnsWithTheOutgoingSegment() {
        val s = slots(line(p(0f, 0f), p(10f, 0f), p(10f, 10f)))
        assertEquals(listOf(0f, -90f, -90f), s.map { it.yawDegrees })
    }

    @Test
    fun moreThanAThousandCopiesAreRefused() {
        assertNull(PathLayout().slots(line(p(0f, 0f), p(2000f, 0f), spacing = 1f)))
        assertNull(PathLayout().slots(line(p(0f, 0f), p(600f, 0f), spacing = 1f, sides = PathSides.BOTH)))
        assertEquals(1000, slots(line(p(0f, 0f), p(999f, 0f), spacing = 1f)).size)
    }

    @Test
    fun unusableSettingsMakeNoSlots() {
        assertEquals(0, slots(line(p(0f, 0f))).size)
        assertEquals(0, slots(line(p(0f, 0f), p(10f, 0f), spacing = 0f)).size)
    }
}
