/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTextTest {
    @Test fun theStatusJoinsToolSpaceAndSnap() {
        assertEquals("Move · Local · Snap 0.5", toolStatus("Move", "Local", "Snap 0.5"))
    }

    @Test fun withSnapOffTheStatusEndsWithTheSpace() {
        assertEquals("Rotate · World", toolStatus("Rotate", "World", null))
    }

    @Test fun stepsFormatWithoutTrailingZeros() {
        assertEquals("0.5", formatStep(0.5f))
        assertEquals("1", formatStep(1f))
        assertEquals("0.25", formatStep(0.25f))
    }

    @Test fun theStatsLineBeginsWithTheFrameRateOnlyWhenItIsShown() {
        assertEquals("12 objects \u00b7 Move \u00b7 Local", statsLine(null, "12 objects", "Move \u00b7 Local"))
        assertEquals("60 fps \u00b7 12 objects \u00b7 Move \u00b7 Local", statsLine(60, "12 objects", "Move \u00b7 Local"))
    }
}
