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
import javax.swing.JPanel

class RenderLoopTest {
    @Test
    fun componentNotOnScreenIsNotRendered() {
        assertFalse(canRender(JPanel()))
    }

    @Test
    fun rendersOnlyWhenShowingSizedAndNotMinimized() {
        assertTrue(canRender(showing = true, width = 800, height = 600, minimized = false))
        assertFalse(canRender(showing = false, width = 800, height = 600, minimized = false))
        assertFalse(canRender(showing = true, width = 800, height = 600, minimized = true))
    }

    /** A zero-sized surface makes macOS Metal reject the backing texture and abort the JVM (seen on switching to the Text tab). */
    @Test
    fun neverRendersIntoAnEmptySurface() {
        assertFalse(canRender(showing = true, width = 0, height = 600, minimized = false))
        assertFalse(canRender(showing = true, width = 800, height = 0, minimized = false))
        assertFalse(canRender(showing = true, width = 0, height = 0, minimized = false))
        assertFalse(canRender(showing = true, width = -1, height = 5, minimized = false))
    }
}
