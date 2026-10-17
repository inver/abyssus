/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerOverlayLayoutTest {
    @Test fun theUpperRightOverlaySitsAMarginFromTheTopAndRightEdges() {
        assertEquals(PixelRect(800 - 12 - 64, 12, 64, 64), cornerRect(Corner.UPPER_RIGHT, 800, 600, 64, 64, 12))
    }

    @Test fun theLowerLeftOverlaySitsAMarginFromTheBottomAndLeftEdges() {
        assertEquals(PixelRect(12, 600 - 12 - 26, 200, 26), cornerRect(Corner.LOWER_LEFT, 800, 600, 200, 26, 12))
    }

    @Test fun resizingKeepsTheOverlayInItsCorner() {
        val small = cornerRect(Corner.UPPER_RIGHT, 400, 300, 64, 64, 12)
        val big = cornerRect(Corner.UPPER_RIGHT, 1600, 900, 64, 64, 12)
        // the gap to the right edge is the same at any width
        assertEquals(400 - (small.x + small.width), 1600 - (big.x + big.width))
        assertEquals(small.y, big.y)
    }

    @Test fun scalingTheArgumentsScalesTheRect() {
        val logical = cornerRect(Corner.UPPER_RIGHT, 800, 600, 64, 64, 12)
        val device = cornerRect(Corner.UPPER_RIGHT, 1600, 1200, 128, 128, 24)
        assertEquals(PixelRect(logical.x * 2, logical.y * 2, logical.width * 2, logical.height * 2), device)
    }

    @Test fun containsIsHalfOpen() {
        val rect = PixelRect(10, 10, 20, 20)
        assertTrue(rect.contains(10, 10))
        assertTrue(rect.contains(29, 29))
        assertFalse(rect.contains(30, 20))
        assertFalse(rect.contains(9, 20))
    }
}
