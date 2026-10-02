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
