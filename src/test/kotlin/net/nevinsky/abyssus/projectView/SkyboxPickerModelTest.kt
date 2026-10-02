/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
