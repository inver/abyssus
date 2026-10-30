/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import org.junit.Assert.*
import org.junit.Test
import java.awt.Canvas
import java.awt.image.BufferedImage
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

class CanvasOverlayHostTest {
    @Test fun overlappingOverlayHidesNativeCanvasAndRestoresItOnDismissal() = SwingUtilities.invokeAndWait {
        val canvas = Canvas()
        var captures = 0
        val host = CanvasOverlayHost(canvas) {
            captures++
            BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
        }
        val layers = JLayeredPane().apply { setSize(200, 200) }
        host.setBounds(0, 0, 100, 100)
        layers.add(host, JLayeredPane.DEFAULT_LAYER, 0)
        host.doLayout()
        val balloon = JPanel().apply { setBounds(50, 50, 100, 100); isOpaque = false }
        layers.add(balloon, JLayeredPane.POPUP_LAYER, 0)
        host.syncOverlay()
        assertFalse("The native surface must not cover the balloon", canvas.isVisible)
        assertEquals(1, captures)
        host.syncOverlay()
        assertEquals("Do not read GL again while hidden", 1, captures)
        host.stop()
        assertFalse("Stopping before disposal must not expose an unstable native surface", canvas.isVisible)
        balloon.isVisible = false
        host.syncOverlay()
        assertTrue(canvas.isVisible)
    }

    @Test fun nonOverlappingAndLowerLayersKeepCanvasLive() = SwingUtilities.invokeAndWait {
        val canvas = Canvas()
        val host = CanvasOverlayHost(canvas) { error("No snapshot needed") }
        val layers = JLayeredPane().apply { setSize(200, 200) }
        host.setBounds(0, 0, 100, 100)
        layers.add(host, JLayeredPane.DEFAULT_LAYER, 0)
        layers.add(JPanel().apply { setBounds(0, 0, 100, 100) }, Integer.valueOf(-1), 0)
        layers.add(JPanel().apply { setBounds(120, 120, 50, 50) }, JLayeredPane.POPUP_LAYER, 0)
        host.syncOverlay()
        assertTrue(canvas.isVisible)
    }
}
