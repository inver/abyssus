/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.lib.core.editor.pick.GizmoAxis
import net.nevinsky.abyssus.lib.core.editor.pick.OrientationProjection
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.math.roundToInt

/** A viewport overlay that Swing paints and GL shows (a Swing component cannot sit over the heavyweight canvas). */
internal abstract class CornerPainter(val corner: Corner) : JComponent() {
    init {
        isOpaque = false
        isFocusable = false
    }

    /** Changes whenever what is painted changes, so the GL side uploads a new texture only then. */
    var version: Long = 0
        protected set

    /** The size in logical pixels. */
    abstract fun measure(): Dimension

    /** This component painted at [scale] device pixels per logical pixel into a transparent image. */
    fun toImage(scale: Double): BufferedImage {
        val size = measure()
        setSize(size)
        val image = BufferedImage((size.width * scale).roundToInt().coerceAtLeast(1), (size.height * scale).roundToInt().coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.scale(scale, scale)
            paint(g)
        } finally {
            g.dispose()
        }
        return image
    }

    protected fun smooth(g: Graphics): Graphics2D = (g.create() as Graphics2D).apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    companion object {
        val PANEL_FILL = JBColor(Color(0xF7, 0xF8, 0xFA, 0xDD), Color(0x2B, 0x2D, 0x30, 0xD9))
        val PANEL_BORDER = JBColor(Color(0xC9, 0xCC, 0xD6), Color(0x4E, 0x51, 0x57))
        val TEXT = JBColor(Color(0x4B, 0x4E, 0x57), Color(0xB4, 0xB8, 0xBF))
    }
}

/** The orientation widget: a disc with the X, Y and Z axes of the world as the orbit camera sees them. */
internal class OrientationWidget : CornerPainter(Corner.UPPER_RIGHT) {
    var projection: OrientationProjection = OrientationProjection(0f, 0f)
        private set

    private var yaw = Float.NaN
    private var pitch = Float.NaN

    /** Shows the axes for a camera at [yaw] and [pitch]; true when that changed the picture. */
    fun show(yaw: Float, pitch: Float): Boolean {
        if (yaw == this.yaw && pitch == this.pitch) return false
        this.yaw = yaw
        this.pitch = pitch
        projection = OrientationProjection(yaw, pitch)
        version++
        return true
    }

    override fun measure(): Dimension = Dimension(SIZE, SIZE)

    /** The mark under a click at ([x], [y]) in this widget's logical pixels, or null. */
    fun axisAt(x: Int, y: Int): GizmoAxis? {
        val unitX = (x - SIZE / 2f) / ARM
        val unitY = -(y - SIZE / 2f) / ARM
        return projection.hit(unitX, unitY, MARK_RADIUS / ARM)?.axis
    }

    override fun paintComponent(graphics: Graphics) {
        val g = smooth(graphics)
        try {
            val disc = Ellipse2D.Float(0.5f, 0.5f, SIZE - 1f, SIZE - 1f)
            g.color = PANEL_FILL
            g.fill(disc)
            g.color = PANEL_BORDER
            g.stroke = BasicStroke(1f)
            g.draw(disc)
            g.font = JBUI.Fonts.miniFont().deriveFont(Font.BOLD, 9f)
            for (mark in projection.marks()) {
                val cx = SIZE / 2f + mark.x * ARM
                val cy = SIZE / 2f - mark.y * ARM
                val color = Color(mark.axis.red, mark.axis.green, mark.axis.blue)
                g.color = color
                g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(java.awt.geom.Line2D.Float(SIZE / 2f, SIZE / 2f, cx, cy))
                g.fill(Ellipse2D.Float(cx - MARK_RADIUS, cy - MARK_RADIUS, MARK_RADIUS * 2, MARK_RADIUS * 2))
                g.color = Color.WHITE
                val label = mark.axis.name
                val metrics = g.fontMetrics
                g.drawString(label, cx - metrics.stringWidth(label) / 2f, cy + (metrics.ascent - metrics.descent) / 2f)
            }
        } finally {
            g.dispose()
        }
    }

    companion object {
        const val SIZE = 64

        /** How far a mark center sits from the middle at full length. */
        const val ARM = 22f
        const val MARK_RADIUS = 6f
    }
}

/** The stats strip: a rounded label with the numbers and tool names of the view. */
internal class StatsStrip : CornerPainter(Corner.LOWER_LEFT) {
    var text: String = ""
        private set

    /** Shows [value]; true when that changed the picture. */
    fun show(value: String): Boolean {
        if (value == text) return false
        text = value
        version++
        return true
    }

    private val statsFont: Font get() = Font(Font.MONOSPACED, Font.PLAIN, 11)

    override fun measure(): Dimension {
        val metrics = getFontMetrics(statsFont)
        return Dimension(metrics.stringWidth(text) + 2 * PAD_X, HEIGHT)
    }

    override fun paintComponent(graphics: Graphics) {
        val g = smooth(graphics)
        try {
            val shape = RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, 8f, 8f)
            g.color = PANEL_FILL
            g.fill(shape)
            g.color = PANEL_BORDER
            g.stroke = BasicStroke(1f)
            g.draw(shape)
            g.font = statsFont
            g.color = TEXT
            val metrics = g.fontMetrics
            g.drawString(text, PAD_X.toFloat(), (height + metrics.ascent - metrics.descent) / 2f)
        } finally {
            g.dispose()
        }
    }

    companion object {
        const val HEIGHT = 26
        const val PAD_X = 10
    }
}
