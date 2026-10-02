/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

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
