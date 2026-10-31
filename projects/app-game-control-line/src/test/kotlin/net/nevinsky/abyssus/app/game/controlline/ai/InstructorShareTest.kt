/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.flight.Takeover
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructorShareTest {
    private val share = InstructorShare()
    private var time = 0f

    private fun run(seconds: Float, danger: Float) = repeat(Math.round(seconds / PHYSICS_STEP)) {
        time += PHYSICS_STEP
        share.step(danger, time)
    }

    @Test
    fun theShareRisesToTheDangerAtOnce() {
        run(PHYSICS_STEP, 0.7f)
        assertEquals(0.7f, share.share, 0f)
        assertTrue(share.inTakeover)
    }

    @Test
    fun itHoldsForASecondThenFallsOverHalfASecond() {
        run(PHYSICS_STEP, 1f)
        run(0.95f, 0f)
        assertEquals(1f, share.share, 0f)
        run(0.3f, 0f)
        assertEquals("a quarter second into the fall", 0.5f, share.share, 0.05f)
        run(0.4f, 0f)
        assertEquals(0f, share.share, 0f)
        assertEquals(1, share.takeovers.size)
        assertFalse(share.inTakeover)
    }

    @Test
    fun itRisesAgainDuringTheFall() {
        run(PHYSICS_STEP, 1f)
        run(1.25f, 0f)
        run(PHYSICS_STEP, 0.8f)
        assertEquals(0.8f, share.share, 0.01f)
        run(PHYSICS_STEP, 1f)
        assertEquals(1f, share.share, 0f)
    }

    @Test
    fun aSmallPeakOpensNoTakeover() {
        run(PHYSICS_STEP, 0.3f)
        run(2f, 0f)
        assertEquals(0f, share.share, 0f)
        assertTrue(share.takeovers.isEmpty())
    }

    @Test
    fun twoTakeoversKeepTheirTimes() {
        run(0.5f, 1f)
        run(2f, 0f)
        val first = share.takeovers.single()
        run(0.5f, 1f)
        run(2f, 0f)
        assertEquals(2, share.takeovers.size)
        assertEquals(Takeover(PHYSICS_STEP, first.end), first)
        // 0.5 s of danger, 1 s of hold and a quarter of the fall to below half
        assertEquals(1.75f, first.end, 0.05f)
        assertTrue(share.takeovers[1].start > first.end)
    }
}
