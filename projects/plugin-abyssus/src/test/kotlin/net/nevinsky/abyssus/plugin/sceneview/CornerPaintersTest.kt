/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.pick.GizmoAxis

/** The Swing painters of the viewport overlays, which GL shows as textures. */
class CornerPaintersTest : BasePlatformTestCase() {
    fun testTheOrientationWidgetPaintsATransparentCornerAndAnOpaqueMark() {
        val widget = OrientationWidget()
        widget.show(0f, 0f)
        val image = widget.toImage(1.0)
        assertEquals(OrientationWidget.SIZE, image.width)
        assertEquals(OrientationWidget.SIZE, image.height)
        assertEquals("outside the disc is transparent", 0, image.getRGB(0, 0) ushr 24)
        // looking down -Z the X mark sits to the right of the center
        val markX = OrientationWidget.SIZE / 2 + OrientationWidget.ARM.toInt()
        assertTrue("the X mark is painted", (image.getRGB(markX, OrientationWidget.SIZE / 2 + 5) ushr 24) > 0)
    }

    fun testTheWidgetScalesWithTheDisplayScale() {
        val widget = OrientationWidget().apply { show(0.3f, 0.2f) }
        assertEquals(OrientationWidget.SIZE * 2, widget.toImage(2.0).width)
    }

    fun testShowReportsOnlyRealChanges() {
        val widget = OrientationWidget()
        assertTrue(widget.show(0.1f, 0.2f))
        val version = widget.version
        assertFalse(widget.show(0.1f, 0.2f))
        assertEquals(version, widget.version)
        assertTrue(widget.show(0.2f, 0.2f))
        assertTrue(widget.version > version)
    }

    fun testAnAxisIsFoundAtItsMarkAndNotAtTheDiscEdge() {
        val widget = OrientationWidget().apply { show(0f, 0f) }
        val center = OrientationWidget.SIZE / 2
        assertEquals(GizmoAxis.X, widget.axisAt(center + OrientationWidget.ARM.toInt(), center))
        assertEquals(GizmoAxis.Y, widget.axisAt(center, center - OrientationWidget.ARM.toInt()))
        assertNull(widget.axisAt(2, OrientationWidget.SIZE - 2))
    }

    fun testTheStripGrowsWithItsTextAndPaintsAboveZeroAlpha() {
        val strip = StatsStrip()
        strip.show("12 objects")
        val short = strip.toImage(1.0)
        strip.show("60 fps · 12 objects · Move · Local · Snap 0.5")
        val long = strip.toImage(1.0)
        assertEquals(StatsStrip.HEIGHT, long.height)
        assertTrue(long.width > short.width)
        assertTrue((long.getRGB(long.width / 2, long.height / 2) ushr 24) > 0)
    }

    fun testSpritesLiveInTheirCorners() {
        assertEquals(Corner.UPPER_RIGHT, OrientationWidget().corner)
        assertEquals(Corner.LOWER_LEFT, StatsStrip().corner)
    }
}
