/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain

import net.nevinsky.abyssus.editor.terrain.FolderNameError
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import net.nevinsky.abyssus.AbyssusCore
import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.JButton

class NewTerrainFormTest : BasePlatformTestCase() {
    private lateinit var projectDir: File
    private val queue = ArrayDeque<Runnable>()
    private var deferred = false

    override fun setUp() {
        super.setUp()
        projectDir = FileUtil.createTempDirectory("abyssus-form", null)
        File(projectDir, "assets/existing").mkdirs()
    }

    private fun form() = NewTerrainForm(
        projectDir, service<AbyssusCore>().terrainGenerator,
        background = { if (deferred) queue += it else it.run() }, ui = { it.run() },
    )

    private fun find(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { find(it, name) }

    private fun text(f: NewTerrainForm, name: String) = find(f, name) as JBTextField
    private fun type(f: NewTerrainForm, name: String, value: String) = text(f, name).let { it.text = value; it.postActionEvent() }
    private fun click(f: NewTerrainForm, name: String) = (find(f, name) as JButton).doClick()

    fun testNoRequestUntilTheNameIsValidAndAMatchingPreviewExists() {
        val f = form()
        assertNull(f.request())
        type(f, "new-terrain-name", "hills")
        type(f, "new-terrain-resolution", "17")
        assertNull("no preview yet", f.request())
        click(f, "terrain-preview")
        val request = f.request()!!
        assertEquals("hills", request.name)
        assertEquals(17, request.preview.resolution)
        assertEquals(1600, request.preview.size)
        assertEquals(17 * 17, request.preview.heights.size)
    }

    fun testChangingASettingSizeOrResolutionDropsThePreview() {
        val f = form()
        type(f, "new-terrain-name", "hills")
        type(f, "new-terrain-resolution", "9")
        click(f, "terrain-preview")
        assertNotNull(f.request())
        type(f, "terrain-gen-seed", "5")
        assertNull(f.request())
        click(f, "terrain-preview")
        assertNotNull(f.request())
        type(f, "new-terrain-size", "800")
        assertNull(f.request())
        click(f, "terrain-preview")
        assertEquals(800, f.request()!!.preview.size)
        type(f, "new-terrain-resolution", "10")
        assertNull(f.request())
        click(f, "terrain-randomize")
        click(f, "terrain-preview")
        assertEquals(10, f.request()!!.preview.resolution)
    }

    fun testTheNameDoesNotInvalidateAPreviewButABadOneRefusesTheRequest() {
        val f = form()
        type(f, "new-terrain-name", "hills")
        type(f, "new-terrain-resolution", "9")
        click(f, "terrain-preview")
        type(f, "new-terrain-name", "other")
        assertEquals("other", f.request()!!.name)
        type(f, "new-terrain-name", "existing")
        assertNull(f.request())
        assertEquals(FolderNameError.EXISTS, f.nameError())
        assertEquals("An asset or file with this name already exists.", (find(f, "new-terrain-error") as JBLabel).text)
        type(f, "new-terrain-name", "../x")
        assertNull(f.request())
        type(f, "new-terrain-name", "fine")
        assertNotNull(f.request())
    }

    fun testInvalidGeometryAndSettingsExplainAndBlockThePreview() {
        val f = form()
        type(f, "new-terrain-name", "hills")
        type(f, "new-terrain-resolution", "1")
        assertEquals("The resolution must be a whole number from 2 to 255.", (find(f, "new-terrain-error") as JBLabel).text)
        assertFalse((find(f, "terrain-preview") as JButton).isEnabled)
        type(f, "new-terrain-resolution", "256")
        assertFalse((find(f, "terrain-preview") as JButton).isEnabled)
        type(f, "new-terrain-resolution", "255")
        assertTrue((find(f, "terrain-preview") as JButton).isEnabled)
        type(f, "new-terrain-size", "0")
        assertEquals("The world size must be a whole number above zero.", (find(f, "new-terrain-error") as JBLabel).text)
        type(f, "new-terrain-size", "800")
        type(f, "terrain-gen-featureSize", "-1")
        assertFalse((find(f, "terrain-preview") as JButton).isEnabled)
        assertTrue((find(f, "terrain-message") as JBLabel).text.contains("Feature size must be a number above zero."))
    }

    fun testDisposingDiscardsADraftAndAPendingPreviewWithoutWriting() {
        val f = form()
        type(f, "new-terrain-name", "hills")
        type(f, "new-terrain-resolution", "9")
        deferred = true
        click(f, "terrain-preview")
        f.dispose()
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertNull(f.request())
        assertEquals(listOf("existing"), File(projectDir, "assets").list()!!.toList())
    }
}
