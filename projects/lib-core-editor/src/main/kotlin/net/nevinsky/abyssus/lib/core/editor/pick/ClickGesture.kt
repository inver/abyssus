/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import kotlin.math.abs

/** Tells a click from a drag: a press and release that never moved more than [slop] pixels from the press. */
class ClickGesture(private val slop: Int = 4) {
    private var startX = 0
    private var startY = 0
    private var active = false
    private var moved = false

    fun pressed(x: Int, y: Int) {
        startX = x
        startY = y
        active = true
        moved = false
    }

    fun dragged(x: Int, y: Int) {
        if (active && (abs(x - startX) > slop || abs(y - startY) > slop)) moved = true
    }

    /** True when the gesture that ends here was a click. */
    fun released(x: Int, y: Int): Boolean {
        dragged(x, y)
        val click = active && !moved
        active = false
        return click
    }
}
