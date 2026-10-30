/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Path

class MainTest {
    @Test fun withoutTheRayOptionRunsRasterWithNoReason() {
        System.clearProperty("controlLine.fieldView")
        val result = resolveFieldView(Path.of("project/ControlLine"))
        assertTrue("Expected Unavailable with no reason when ray is not requested", result is RayFieldResult.Unavailable)
        assertNull((result as RayFieldResult.Unavailable).reason)
    }

    @Test fun rayRequestedButRaySourceSetAbsentReportsUnavailable() {
        System.setProperty("controlLine.fieldView", "ray")
        try {
            val result = resolveFieldView(Path.of("project/ControlLine"))
            assertTrue("Expected Unavailable when the ray source set is not on the classpath", result is RayFieldResult.Unavailable)
            assertNotNull((result as RayFieldResult.Unavailable).reason)
        } finally {
            System.clearProperty("controlLine.fieldView")
        }
    }
}
