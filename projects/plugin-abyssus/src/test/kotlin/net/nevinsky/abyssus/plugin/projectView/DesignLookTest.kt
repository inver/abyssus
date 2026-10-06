/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.render.RenderingUtil
import net.nevinsky.abyssus.plugin.filetype.EyeIcons
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.JComponent
import net.nevinsky.abyssus.plugin.testMetaFiles
import net.nevinsky.abyssus.plugin.testCore

class DesignLookTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun paint(c: JComponent, width: Int = 480): BufferedImage {
        c.setSize(width, c.preferredSize.height)
        c.doLayout()
        c.components.forEach { (it as? JComponent)?.doLayout() }
        val image = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        c.paint(g)
        g.dispose()
        return image
    }

    private fun dialog(choices: List<SkyboxChoice>) = SkyboxChooserDialog(project, choices, null, testCore.hdrPreviews.preview).also { Disposer.register(testRootDisposable, it.disposable) }

    private val choices = listOf(SkyboxChoice("nebula", 6, listOf("png"), 1, false), SkyboxChoice("dusk", 6, listOf("png"), 0, true))

    // selection highlight

    fun testSelectedChooserRowIsAHighlightedRoundedBlockInsetFromTheEdges() {
        val d = dialog(choices)
        val image = paint(d.renderedRow(1, selected = true))
        val inside = Color(image.getRGB(image.width / 2, image.height - 4), true)
        assertEquals(DesignColors.SELECTION.rgb, inside.rgb)
        assertEquals("the highlight is inset from the list edge", 0, Color(image.getRGB(2, image.height / 2), true).alpha)
        assertEquals("rounded: the corner stays clear", 0, Color(image.getRGB(7, 0), true).alpha)
    }

    fun testUnselectedChooserRowHasNoHighlight() {
        val d = dialog(choices)
        val image = paint(d.renderedRow(1, selected = false))
        assertEquals(0, Color(image.getRGB(image.width / 2, image.height - 4), true).alpha)
    }

    fun testChooserRowsAreTheDesignsHeight() {
        val d = dialog(choices)
        assertTrue(d.renderedRow(1, false).preferredSize.height >= 56)
    }

    fun testTreeUsesTheDesignSelectionColour() {
        val pane = AbyssusProjectViewPane(project)
        try {
            pane.createComponent()
            val supplier = pane.tree.getClientProperty(RenderingUtil.CUSTOM_SELECTION_BACKGROUND) as java.util.function.Supplier<*>
            assertEquals(DesignColors.SELECTION, supplier.get())
        } finally {
            Disposer.dispose(pane)
        }
    }

    // face thumbnails

    fun testChooserRowsGetFaceThumbnailsFromTheAssetFolder() {
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1}""")
        myFixture.copyFileToProject("Untitled/assets/skybox_default/skybox_default.png", "p/assets/sky/skybox_default.png")
        myFixture.addFileToProject("p/assets/sky/meta.json", """{"format":"abyssus","formatVersion":1,"type":"SKYBOX","additional":{"top":"skybox_default.png","bottom":"skybox_default.png","left":"skybox_default.png","right":"skybox_default.png","front":"skybox_default.png","back":"missing.png"}}""")
        val abss = myFixture.findFileInTempDir("p/P.abss")
        myFixture.addFileToProject("p/scenes/S.scene", """{"format":"abyssus","formatVersion":1}""")
        val loaded = loadSkyboxChoices(project, abss, testMetaFiles(), testCore.hdrPreviews)!!
        val sky = loaded.single()
        assertEquals(listOf("skybox_default.png", "skybox_default.png", "skybox_default.png", "skybox_default.png", "skybox_default.png", "missing.png"), sky.faceFiles)
        dialog(loaded)
        PlatformTestUtil.waitWithEventsDispatching("thumbnails were not loaded", { sky.thumbs.isNotEmpty() }, 15)
        assertEquals(6, sky.thumbs.size)
        assertEquals(5, sky.thumbs.count { it != null })
        assertNull("a missing face stays an empty cell", sky.thumbs[5])
    }

    // enable / disable eye

    fun testEyeIsTealWhenOnAndGrayWhenOff() {
        assertEquals(16, EyeIcons.ON.iconWidth)
        assertNotSame(EyeIcons.ON, EyeIcons.OFF)
        fun dominant(icon: javax.swing.Icon): Color {
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            icon.paintIcon(null, g, 0, 0)
            g.dispose()
            var r = 0L; var gr = 0L; var b = 0L; var n = 0
            for (x in 0 until 16) for (y in 0 until 16) {
                val c = Color(image.getRGB(x, y), true)
                if (c.alpha > 128) { r += c.red; gr += c.green; b += c.blue; n++ }
            }
            assertTrue("the icon draws something", n > 0)
            return Color((r / n).toInt(), (gr / n).toInt(), (b / n).toInt())
        }
        val on = dominant(EyeIcons.ON)
        val off = dominant(EyeIcons.OFF)
        assertTrue("on is teal (blue/green over red): $on", on.blue > on.red + 60 && on.green > on.red + 60)
        assertTrue("off is gray: $off", Math.abs(off.red - off.blue) < 30 && Math.abs(off.red - off.green) < 30)
    }
}
