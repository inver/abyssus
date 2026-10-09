/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JLabel
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultMutableTreeNode

class RowActionsTest : BasePlatformTestCase() {
    private val host = JLabel("x")

    fun testEmptyTreePaintsWithoutRowBounds() {
        val tree = EyeTree(DefaultTreeModel(DefaultMutableTreeNode("root")), project)
        tree.isRootVisible = false
        tree.setSize(400, 200)
        assertEquals(0, tree.rowCount)
        assertNull(tree.getRowBounds(-1))
        val image = BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setClip(0, 0, 400, 200)
            tree.paint(g)
        } finally {
            g.dispose()
            javax.swing.ToolTipManager.sharedInstance().unregisterComponent(tree)
        }
    }

    private fun entryOf(unused: Boolean) =
        DtoEntry("/p/assets/0", "0", net.nevinsky.abyssus.lib.gdx.editor.testAsset("a", "u", "SKYBOX", emptyList(), unused), null, null, null, emptyList())

    private fun paint(action: RowAction): BufferedImage {
        val w = action.width(host)
        val h = action.height(host)
        val image = BufferedImage(w + 4, h + 4, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        action.paint(g, Rectangle(2, 2, w, h), host)
        g.dispose()
        return image
    }

    private fun opaquePixels(image: BufferedImage, test: (Color) -> Boolean) =
        (0 until image.width).sumOf { x -> (0 until image.height).count { y -> Color(image.getRGB(x, y), true).let { it.alpha > 128 && test(it) } } }

    fun testOnlyUnusedAssetsGetTheBadge() {
        assertTrue(unusedBadgeFor(entryOf(true)) is UnusedBadge)
        assertNull(unusedBadgeFor(entryOf(false)))
        assertNull(unusedBadgeFor(DtoEntry("/p/x", "x", "text", null, null, null, emptyList())))
    }

    fun testBadgeIsAnEighteenPixelAmberOutlineWithAmberText() {
        val badge = UnusedBadge()
        assertEquals(18, badge.height(host))
        assertNull(badge.run)
        assertNull(badge.tooltip)
        val image = paint(badge)
        assertTrue("outline is drawn", opaquePixels(image) { it.rgb == UnusedBadge.BORDER.rgb } > 20)
        assertTrue("text is drawn in amber", opaquePixels(image) { Math.abs(it.red - UnusedBadge.TEXT.red) < 40 && Math.abs(it.green - UnusedBadge.TEXT.green) < 40 && it.blue < 120 } > 5)
    }

    fun testChooseButtonIsATwentyFourPixelOutlinedClickableButton() {
        var clicked = -1
        val choose = ChooseButton("Choose skybox...") { clicked = it }
        assertEquals(24, choose.height(host))
        assertEquals("Choose skybox...", choose.tooltip)
        choose.run!!.invoke(3)
        assertEquals(3, clicked)
        assertTrue(choose.width(host) > 24 + host.getFontMetrics(host.font).stringWidth("Choose"))
        val image = paint(choose)
        assertTrue("outline is drawn", opaquePixels(image) { it.rgb == ChooseButton.BORDER.rgb } > 30)
        assertTrue("icon and label are drawn", opaquePixels(image) { it.rgb != ChooseButton.BORDER.rgb } > 20)
    }

    fun testActionsSitRightToLeftWithGapsAndAreCentredOnTheRow() {
        val eye = IconAction(net.nevinsky.abyssus.plugin.filetype.EyeIcons.ON, null) { }
        val choose = ChooseButton(null) { }
        val badge = UnusedBadge()
        val row = Rectangle(0, 100, 400, 28)
        val bounds = layoutActions(row, 400, listOf(eye, choose, badge), host)
        assertEquals(400 - 8 - 16, bounds[0].x)
        assertEquals(bounds[0].x - 8 - choose.width(host), bounds[1].x)
        assertEquals(bounds[1].x - 8 - badge.width(host), bounds[2].x)
        assertTrue("the badge is the leftmost", bounds[2].x < bounds[1].x && bounds[1].x < bounds[0].x)
        bounds.forEachIndexed { i, b ->
            assertTrue("action $i is centred", Math.abs((row.y + row.height / 2) - (b.y + b.height / 2)) <= 1)
            if (i > 0) assertTrue("no overlap", b.x + b.width <= bounds[i - 1].x)
        }
    }
}
