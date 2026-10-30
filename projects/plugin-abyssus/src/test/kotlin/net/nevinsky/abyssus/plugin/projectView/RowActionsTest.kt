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
        DtoEntry("/p/assets/0", "0", net.nevinsky.abyssus.lib.core.editor.testAsset("a", "u", "SKYBOX", emptyList(), unused), null, null, null, emptyList())

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

    fun testUnusedIsPlainAmberTextWithoutOutline() {
        val badge = UnusedBadge()
        assertNull(badge.run)
        assertNull(badge.tooltip)
        val image = paint(badge)
        assertTrue("text is drawn in amber", opaquePixels(image) { Math.abs(it.red - UnusedBadge.TEXT.red) < 40 && Math.abs(it.green - UnusedBadge.TEXT.green) < 40 && it.blue < 120 } > 5)
        // no outline: the left-most column inside the bounds is not a full-height border line
        val column = (0 until image.height).count { Color(image.getRGB(2, it), true).alpha > 128 }
        assertTrue("no border line", column < badge.height(host))
    }

    fun testTheTreeHasNoChooseButton() {
        val actions = EyeTree::class.java.declaredMethods.map { it.name }
        assertFalse(actions.any { it.contains("skybox", ignoreCase = true) })
    }

    fun testActionsSitRightToLeftWithGapsAndAreCentredOnTheRow() {
        val eye = IconAction(net.nevinsky.abyssus.plugin.filetype.EyeIcons.ON, null) { }
        val badge = UnusedBadge()
        val row = Rectangle(0, 100, 400, 28)
        val bounds = layoutActions(row, 400, listOf(eye, badge), host)
        assertEquals(400 - 8 - eye.width(host), bounds[0].x)
        assertEquals(bounds[0].x - 8 - badge.width(host), bounds[1].x)
        assertTrue("unused is left of the eye", bounds[1].x + bounds[1].width <= bounds[0].x)
        bounds.forEachIndexed { i, b ->
            assertTrue("action $i is centred", Math.abs((row.y + row.height / 2) - (b.y + b.height / 2)) <= 1)
            if (i > 0) assertTrue("no overlap", b.x + b.width <= bounds[i - 1].x)
        }
    }
}
