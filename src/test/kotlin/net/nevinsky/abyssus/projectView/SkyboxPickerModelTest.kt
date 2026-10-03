/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.AbyssusBundle

class SkyboxPickerModelTest : BasePlatformTestCase() {
    private fun choice(name: String, sceneCount: Int = 0, unused: Boolean = sceneCount == 0) =
        SkyboxChoice(name, 6, listOf("png"), sceneCount, unused)

    private val three = listOf(choice("abyss-night"), choice("dusk"), choice("nebula", 1))

    fun testNoneFirstThenSkyboxes() {
        val m = SkyboxPickerModel(listOf(choice("skybox_default")), null)
        assertEquals(listOf(null, "skybox_default"), m.entries.map { it?.name })
        assertEquals("1 found", m.foundText)
    }

    fun testNoSkyboxesListsOnlyNone() {
        val m = SkyboxPickerModel(emptyList(), null)
        assertEquals(listOf<SkyboxChoice?>(null), m.entries)
        assertEquals("0 found", m.foundText)
    }

    fun testFilterIgnoresCase() {
        val m = SkyboxPickerModel(three, null)
        m.filter = "NIGHT"
        assertEquals(listOf(null, "abyss-night"), m.entries.map { it?.name })
        assertEquals("1 found", m.foundText)
        assertFalse(m.noMatch)
    }

    fun testFilterStillAppliesToProceduralSkies() {
        val m = SkyboxPickerModel(three + SkyboxChoice("skybox_physical", 0, emptyList(), 0, true, procedural = true), null)
        m.filter = "PHYS"
        assertEquals(listOf(null, "skybox_physical"), m.entries.map { it?.name })
        assertEquals("1 found", m.foundText)
    }

    fun testNothingMatches() {
        val m = SkyboxPickerModel(three, null)
        m.filter = "xyz"
        assertEquals(listOf<SkyboxChoice?>(null), m.entries)
        assertEquals("0 found", m.foundText)
        assertTrue(m.noMatch)
    }

    fun testPreselection() {
        assertEquals("nebula", SkyboxPickerModel(three, "nebula").selected)
        assertNull(SkyboxPickerModel(three, null).selected)
        assertNull("an unlisted skybox selects None", SkyboxPickerModel(three, "gone").selected)
    }

    fun testFooter() {
        val m = SkyboxPickerModel(three, "nebula")
        assertEquals("Selected: nebula", m.footerText)
        m.selected = null
        assertEquals("Selected: none", m.footerText)
    }

    fun testSelectionSurvivesTheFilter() {
        val m = SkyboxPickerModel(three, "nebula")
        m.filter = "dusk"
        assertEquals("nebula", m.selected)
        assertEquals("Selected: nebula", m.footerText)
    }

    fun testUsageText() {
        assertEquals("used by 1 scene", AbyssusBundle.message("skyboxUsedBy", 1))
        assertEquals("used by 2 scenes", AbyssusBundle.message("skyboxUsedBy", 2))
    }
}
