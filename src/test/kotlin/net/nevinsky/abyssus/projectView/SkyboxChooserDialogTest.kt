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

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SkyboxChooserDialogTest : BasePlatformTestCase() {
    private val choices = listOf(
        SkyboxChoice("abyss-night", 6, listOf("png"), 0, true),
        SkyboxChoice("dusk", 6, listOf("png"), 0, true),
        SkyboxChoice("nebula", 6, listOf("png"), 1, false),
    )

    private fun dialog(current: String?) = SkyboxChooserDialog(project, choices, current).also {
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
