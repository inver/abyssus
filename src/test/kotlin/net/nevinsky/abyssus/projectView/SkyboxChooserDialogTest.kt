/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.testCore

class SkyboxChooserDialogTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private val choices = listOf(
        SkyboxChoice("abyss-night", 6, listOf("png"), 0, true),
        SkyboxChoice("dusk", 6, listOf("png"), 0, true),
        SkyboxChoice("nebula", 6, listOf("png"), 1, false),
    )

    private fun dialog(current: String?) = SkyboxChooserDialog(project, choices, current, testCore.hdrPreviews.preview).also {
        Disposer.register(testRootDisposable, it.disposable)
    }

    fun testOpensOnTheCurrentSkybox() {
        val d = dialog("nebula")
        assertEquals("Choose a skybox", d.title)
        assertEquals(listOf(null, "abyss-night", "dusk", "nebula"), d.rows.map { it?.name })
        assertEquals("3 found", d.foundLabel)
        assertEquals("Selected: nebula", d.footerLabel)
        assertEquals("nebula", d.chosen)
        assertFalse(d.noMatchShown)
    }

    fun testFilterAndNoMatchMessage() {
        val d = dialog(null)
        d.typeFilter("NIGHT")
        assertEquals(listOf(null, "abyss-night"), d.rows.map { it?.name })
        assertEquals("1 found", d.foundLabel)
        d.typeFilter("xyz")
        assertEquals(listOf<SkyboxChoice?>(null), d.rows)
        assertEquals("0 found", d.foundLabel)
        assertTrue(d.noMatchShown)
    }

    fun testHdrEntryShowsOneThumbnail() {
        myFixture.copyFileToProject("Untitled/assets/skybox_hdr/sky.hdr", "sky/sky.hdr")
        val hdr = SkyboxChoice("sky", 0, emptyList(), 0, true, hdr = HdrSkyInfo("sky.hdr", 64, 32)).also {
            it.folder = myFixture.findFileInTempDir("sky")
            it.faceFiles = listOf("sky.hdr")
        }
        val d = SkyboxChooserDialog(project, listOf(hdr), null, testCore.hdrPreviews.preview).also { Disposer.register(testRootDisposable, it.disposable) }
        assertEquals(listOf(null, "sky"), d.rows.map { it?.name })
        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching("HDR thumbnail", { hdr.thumbs.isNotEmpty() }, 10)
        assertEquals(1, hdr.thumbs.size)
        val thumb = hdr.thumbs.single()!!
        assertEquals(2 * thumb.height, thumb.width)
    }

    fun testClickingSelectsWithoutClosing() {
        val d = dialog(null)
        assertEquals("Selected: none", d.footerLabel)
        d.clickRow(2)
        assertEquals("dusk", d.chosen)
        assertEquals("Selected: dusk", d.footerLabel)
        d.typeFilter("neb")
        assertEquals("a filtered-out selection is kept", "dusk", d.chosen)
        d.clickRow(0)
        assertNull(d.chosen)
    }
}
