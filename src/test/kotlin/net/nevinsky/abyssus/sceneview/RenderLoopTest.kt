/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
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
