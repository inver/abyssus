/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTest {
    @Test
    fun theOrderAndKFactorsOfRule4212() {
        val expected = listOf(
            "Take-off" to 2, "Reverse wingover" to 8, "Three inside loops" to 6, "Two laps of inverted flight" to 2,
            "Three outside loops" to 6, "Two inside square loops" to 12, "Two outside square loops" to 12,
            "Two inside triangular loops" to 14, "Two horizontal eights" to 7, "Two square horizontal eights" to 18,
            "Two vertical eights" to 10, "Hourglass" to 10, "Two overhead eights" to 10, "Four-leaf clover" to 8, "Landing" to 5,
        )
        assertEquals(expected, SCHEDULE.map { it.label to it.k })
    }

    @Test
    fun theFactorsSumToOneHundredAndThirty() {
        assertEquals(130, TOTAL_K)
        assertEquals(1300, MAX_SCORE)
    }
}
