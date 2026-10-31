/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireLookTest {
    @Test
    fun aWireFifteenMetresAwayIsOnePixelWideAndFaint() {
        val look = wireLook(0.0005f, 15f, 67f, 1080f)
        assertEquals(pixelSize(15f, 67f, 1080f), look.width, 1e-6f)
        assertTrue("opacity ${look.opacity}", look.opacity < 0.2f)
        assertTrue(look.opacity >= WIRE_MIN_OPACITY)
    }

    @Test
    fun aWireCloseUpIsDrawnAtItsTrueThickness() {
        val look = wireLook(0.0005f, 0.5f, 67f, 1080f)
        // 0.5 m away a pixel is 0.6 mm: the wire (0.5 mm) is almost one pixel and nearly opaque
        assertTrue("opacity ${look.opacity}", look.opacity > 0.8f)
        val near = wireLook(0.0005f, 0.2f, 67f, 1080f)
        assertEquals(0.0005f, near.width, 1e-6f)
        assertEquals(1f, near.opacity, 0f)
    }

    @Test
    fun theGlintPeaksWhenTheHalfVectorIsAcrossTheWire() {
        val tangent = Vector3(0f, 0f, 1f)
        val across = wireGlint(tangent, Vector3(0f, 1f, 0f), Vector3(0f, 1f, 0f))
        val along = wireGlint(tangent, Vector3(0f, 0f, 1f), Vector3(0f, 0f, 1f))
        assertEquals(1f, across, 1e-4f)
        assertEquals(0f, along, 1e-4f)
    }
}
