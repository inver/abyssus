/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.assets.Asset
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Something painted at the right edge of a tree row. [run] is null for a purely visual one (the unused badge); the
 * others are clickable and show [tooltip].
 */
internal abstract class RowAction(val tooltip: String?, val run: ((row: Int) -> Unit)?) {
    abstract fun width(c: JComponent): Int

    abstract fun height(c: JComponent): Int

    abstract fun paint(g: Graphics2D, bounds: Rectangle, c: JComponent)
}

/** A bare icon: the eye or "view scene". */
internal class IconAction(private val icon: Icon, tooltip: String?, run: (row: Int) -> Unit) : RowAction(tooltip, run) {
    override fun width(c: JComponent) = icon.iconWidth

    override fun height(c: JComponent) = icon.iconHeight

    override fun paint(g: Graphics2D, bounds: Rectangle, c: JComponent) = icon.paintIcon(c, g, bounds.x, bounds.y)
}

private fun Graphics2D.smooth() = setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

private fun JComponent.sized(px: Float) = font.deriveFont(JBUI.scale(px))

/** The design's amber `unused` tag: small text in a thin rounded outline, 18px high. */
internal class UnusedBadge : RowAction(null, null) {
    private fun text() = AbyssusBundle.message("assetUnused")

    override fun width(c: JComponent) = c.getFontMetrics(c.sized(FONT)).stringWidth(text()) + 2 * JBUI.scale(PADDING) + 2

    override fun height(c: JComponent) = JBUI.scale(HEIGHT)

    override fun paint(g: Graphics2D, bounds: Rectangle, c: JComponent) {
        g.smooth()
        g.color = BORDER
        g.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, JBUI.scale(4), JBUI.scale(4))
        g.font = c.sized(FONT)
        g.color = TEXT
        val fm = g.fontMetrics
        g.drawString(text(), bounds.x + 1 + JBUI.scale(PADDING), bounds.y + (bounds.height - fm.height) / 2 + fm.ascent)
    }

    companion object {
        const val FONT = 11f
        const val PADDING = 6
        const val HEIGHT = 18

        /** `#e0a458` text on `#6b5230` on dark themes; darker amber on light ones. */
        val TEXT: Color = JBColor(0xA8741A, 0xE0A458)
        val BORDER: Color = JBColor(0xD9B26A, 0x6B5230)
    }
}

/** The design's "Choose" button: swap icon and label in a thin rounded outline, 24px high. */
internal class ChooseButton(tooltip: String?, run: (row: Int) -> Unit) : RowAction(tooltip, run) {
    private fun text() = AbyssusBundle.message("skyboxChooseButton")

    override fun width(c: JComponent) =
        2 + JBUI.scale(6) + SWAP.iconWidth + JBUI.scale(4) + c.getFontMetrics(c.sized(FONT)).stringWidth(text()) + JBUI.scale(8)

    override fun height(c: JComponent) = JBUI.scale(HEIGHT)

    override fun paint(g: Graphics2D, bounds: Rectangle, c: JComponent) {
        g.smooth()
        g.color = BORDER
        g.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, JBUI.scale(6), JBUI.scale(6))
        SWAP.paintIcon(c, g, bounds.x + 1 + JBUI.scale(6), bounds.y + (bounds.height - SWAP.iconHeight) / 2)
        g.font = c.sized(FONT)
        g.color = c.foreground
        val fm = g.fontMetrics
        g.drawString(text(), bounds.x + 1 + JBUI.scale(6) + SWAP.iconWidth + JBUI.scale(4), bounds.y + (bounds.height - fm.height) / 2 + fm.ascent)
    }

    companion object {
        const val FONT = 12f
        const val HEIGHT = 24

        val BORDER: Color = JBColor(0xC9CCD6, 0x4E5157)
        val SWAP: Icon = IconLoader.getIcon("/icons/choose_swap_icon.svg", ChooseButton::class.java)
    }
}

/** Space between the actions of a row. */
internal const val ACTION_GAP = 8

/** The unused badge of an asset row, or null for any other row. */
internal fun unusedBadgeFor(entry: DtoEntry): RowAction? = if ((entry.value as? Asset<*>)?.unused == true) UnusedBadge() else null

/**
 * The bounds of [actions] (rightmost first) on the row [row], each vertically centred, placed leftwards from
 * [rightEdge] with [ACTION_GAP] between them.
 */
internal fun layoutActions(row: Rectangle, rightEdge: Int, actions: List<RowAction>, c: JComponent): List<Rectangle> {
    var x = rightEdge
    return actions.map { a ->
        val w = a.width(c)
        val h = a.height(c)
        x -= w + ACTION_GAP
        Rectangle(x, row.y + (row.height - h) / 2, w, h)
    }
}
