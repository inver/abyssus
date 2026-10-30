/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AnglesTest {
    @Test
    fun wrapsIntoOpenNegativeAndClosedPositiveHalfTurn() {
        for ((input, expected) in listOf(180.0 to 180.0, -180.0 to 180.0, 540.0 to 180.0,
            -540.0 to 180.0, 361.25 to 1.25, -361.25 to -1.25)) {
            assertEquals(expected, wrapDegrees(input), 0.0)
            assertEquals(expected.toFloat(), wrapDegrees(input.toFloat()), 0f)
        }
        assertEquals(0.1234567890123, wrapDegrees(0.1234567890123), 0.0)
    }

    @Test
    fun azimuthRunsFromPositiveXTowardNegativeZ() {
        for ((x, z, expected) in listOf(Triple(1.0, 0.0, 0.0), Triple(0.0, -1.0, 90.0),
            Triple(-1.0, -0.0, 180.0), Triple(0.0, 1.0, -90.0))) {
            assertEquals(expected, azimuthOf(x, z), 1e-12)
            assertEquals(expected.toFloat(), azimuthOf(x.toFloat(), z.toFloat()), 1e-5f)
        }
        assertEquals(-Math.PI / 2, azimuthRadiansOf(0.0, 1.0), 0.0)
        assertEquals((-Math.PI / 2).toFloat(), azimuthRadiansOf(0f, 1f), 0f)
    }
}
