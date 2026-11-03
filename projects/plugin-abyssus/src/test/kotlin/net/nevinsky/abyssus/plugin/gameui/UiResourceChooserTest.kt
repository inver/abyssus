/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The choosers of Import Image and Import Font build, and each offers the files its import accepts. */
class UiResourceChooserTest : BasePlatformTestCase() {
    fun testTheFontChooserOffersBitmapFontFilesAndBuilds() {
        val descriptor = resourceChooserDescriptor(font = true)
        assertTrue(descriptor.isFileSelectable(myFixture.addFileToProject("f/ui.fnt", "info face=x").virtualFile))
        assertFalse(descriptor.isFileSelectable(myFixture.addFileToProject("f/ui.png", "x").virtualFile))
    }

    fun testTheImageChooserOffersPngAndJpegFiles() {
        val descriptor = resourceChooserDescriptor(font = false)
        for (name in listOf("a.png", "b.jpg", "c.jpeg")) {
            assertTrue("$name is offered", descriptor.isFileSelectable(myFixture.addFileToProject("i/$name", "x").virtualFile))
        }
        assertFalse(descriptor.isFileSelectable(myFixture.addFileToProject("i/d.fnt", "x").virtualFile))
    }
}
