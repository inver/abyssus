/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.physics

import org.junit.Assert.assertSame
import org.junit.Test

class PhysicsOverlayTest {
    @Test fun toolbarUpdatesRetainThePhysicsAction() {
        val overlay = PhysicsOverlay(null)
        val action = overlay.actions().single()

        repeat(25) {
            overlay.shown = it % 2 == 0
            assertSame(action, overlay.actions().single())
        }
    }
}
