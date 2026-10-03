/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickGestureTest {
    @Test
    fun pressAndReleaseInPlaceIsAClick() {
        val g = ClickGesture()
        g.pressed(10, 10)
        assertTrue(g.released(10, 10))
    }

    @Test
    fun smallJitterStillIsAClick() {
        val g = ClickGesture(4)
        g.pressed(10, 10)
        g.dragged(12, 9)
        assertTrue(g.released(13, 11))
    }

    @Test
    fun aDragIsNotAClickEvenWhenItEndsWhereItStarted() {
        val g = ClickGesture(4)
        g.pressed(10, 10)
        g.dragged(40, 10)
        g.dragged(10, 10)
        assertFalse(g.released(10, 10))
    }

    @Test
    fun releaseWithoutPressIsNotAClickAndGestureEnds() {
        val g = ClickGesture()
        assertFalse(g.released(1, 1))
        g.pressed(1, 1)
        assertTrue(g.released(1, 1))
        assertFalse(g.released(1, 1))
    }
}
