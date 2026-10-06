/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.flight

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashSeverityTest {
    @Test
    fun theImpactSpeedPicksTheSeverity() {
        assertEquals(CrashSeverity.LITTLE, crashSeverity(0f))
        assertEquals(CrashSeverity.LITTLE, crashSeverity(CRASH_SPEED + 0.1f))
        assertEquals(CrashSeverity.LITTLE, crashSeverity(MEDIUM_CRASH_SPEED - 0.01f))
        assertEquals(CrashSeverity.MEDIUM, crashSeverity(MEDIUM_CRASH_SPEED))
        assertEquals(CrashSeverity.MEDIUM, crashSeverity(FULL_CRASH_SPEED - 0.01f))
        assertEquals(CrashSeverity.FULL, crashSeverity(FULL_CRASH_SPEED))
        assertEquals(CrashSeverity.FULL, crashSeverity(40f))
    }

    @Test
    fun eachSeverityNamesATrainerClip() {
        assertEquals(listOf("crash_little", "crash_medium", "crash_full"), CrashSeverity.entries.map { it.clip })
    }
}
