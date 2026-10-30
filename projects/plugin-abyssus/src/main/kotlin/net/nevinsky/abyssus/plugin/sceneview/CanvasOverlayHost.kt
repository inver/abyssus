/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Component
import java.awt.Container
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import javax.swing.Timer

/** Keeps native GL surfaces below lightweight IDE overlays. */
internal class CanvasOverlayHost(
    private val canvas: Canvas,
    private val capture: () -> BufferedImage?,
) : JPanel(BorderLayout()) {
    private var snapshot: BufferedImage? = null
    private val monitor = Timer(50) { syncOverlay() }

    init { add(canvas, BorderLayout.CENTER) }

    override fun addNotify() {
        super.addNotify()
        monitor.start()
    }

    override fun removeNotify() {
        stop()
        super.removeNotify()
        canvas.isVisible = true
    }

    fun stop() {
        monitor.stop()
        snapshot = null
    }

    internal fun syncOverlay() {
        val overlapping = hasOverlay()
        if (overlapping == !canvas.isVisible) return
        if (overlapping) snapshot = capture()
        else snapshot = null
        canvas.isVisible = !overlapping
        revalidate()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        snapshot?.let { g.drawImage(it, 0, 0, width, height, null) }
    }

    private fun hasOverlay(): Boolean {
        var child: Component = this
        var ancestor = parent
        while (ancestor != null) {
            val bounds = SwingUtilities.convertRectangle(this, java.awt.Rectangle(0, 0, width, height), ancestor)
            val index = ancestor.getComponentZOrder(child)
            for (sibling in ancestor.components.take(index.coerceAtLeast(0))) {
                if (!sibling.isVisible || !sibling.bounds.intersects(bounds)) continue
                // IntelliJ's glass pane is often visible solely to intercept events. Its empty area is transparent.
                if (ancestor is JRootPane && sibling === ancestor.glassPane) {
                    val glass = sibling as? Container ?: continue
                    if (glass.components.none { it.isVisible && SwingUtilities.convertRectangle(it.parent, it.bounds, ancestor).intersects(bounds) }) continue
                }
                return true
            }
            child = ancestor
            ancestor = ancestor.parent
        }
        return false
    }
}
