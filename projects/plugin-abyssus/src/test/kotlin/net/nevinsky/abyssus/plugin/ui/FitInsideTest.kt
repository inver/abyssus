/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FitInsideTest {
    @Test
    fun aLargeImageShrinksKeepingItsAspect() {
        assertEquals(256 to 144, fitInside(1920, 1080, 320, 144, upscale = false))
    }

    @Test
    fun aSmallImageIsEnlargedOnlyWhenAsked() {
        assertEquals(10 to 5, fitInside(10, 5, 100, 100, upscale = false))
        assertEquals(100 to 50, fitInside(10, 5, 100, 100, upscale = true))
    }

    @Test
    fun theResultIsNeverEmpty() {
        assertEquals(10 to 1, fitInside(1000, 1, 10, 10, upscale = false))
        assertEquals(1 to 10, fitInside(1, 1000, 10, 10, upscale = false))
    }
}
