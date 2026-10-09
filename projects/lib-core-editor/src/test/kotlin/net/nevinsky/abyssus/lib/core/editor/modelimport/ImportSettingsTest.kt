/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.modelimport

import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSettingsTest {
    private val rules = ImportSettingsRules()
    private val untitled = untitledAssetNames()

    private fun settings(folder: String, fit: FitSize = FitSize.Original) = ImportSettings(folder, LengthUnit.CM, UpAxis.Y, fit)

    @Test
    fun aTakenFolderNameIsRefused() {
        assertTrue("tree" in untitled)
        assertEquals(listOf(SettingsProblem.FOLDER_TAKEN), rules.problems(settings("tree"), untitled))
        assertEquals(listOf(SettingsProblem.FOLDER_TAKEN), rules.problems(settings("Tree"), untitled))
        assertTrue(rules.problems(settings("model_crate"), untitled).isEmpty())
    }

    @Test
    fun aSizeOfZeroIsRefused() {
        assertEquals(listOf(SettingsProblem.SIZE_NOT_POSITIVE), rules.problems(settings("a", FitSize.Height(0.0)), untitled))
        assertEquals(listOf(SettingsProblem.SIZE_NOT_POSITIVE), rules.problems(settings("a", FitSize.LargestExtent(-1.0)), untitled))
        assertTrue(rules.problems(settings("a", FitSize.Height(1.8)), untitled).isEmpty())
    }

    @Test
    fun aNameWithAPathSeparatorIsRefused() {
        for (name in listOf("a/b", "a\\b", "..", "con", "name.", " name", "a:b")) {
            assertEquals(name, listOf(SettingsProblem.FOLDER_INVALID), rules.problems(settings(name), untitled))
        }
        assertEquals(listOf(SettingsProblem.FOLDER_EMPTY), rules.problems(settings(" "), untitled))
    }

    @Test
    fun xUpIsNotOffered() {
        assertEquals(listOf(SettingsProblem.UP_AXIS_UNSUPPORTED), rules.problems(ImportSettings("a", LengthUnit.M, UpAxis.X), untitled))
    }

    @Test
    fun theDefaultNameIsModelAndTheFileStem() {
        assertEquals("model_crate", rules.defaultFolderName("crate.obj", untitled))
        assertEquals("model_my_crate", rules.defaultFolderName("My Crate.obj", untitled))
        assertEquals("model_crate_2", rules.defaultFolderName("crate.fbx", untitled + "model_crate"))
        assertEquals("model_model", rules.defaultFolderName("###.obj", untitled))
    }

    @Test
    fun unitsAreMatchedByTheirSize() {
        assertEquals(LengthUnit.CM, lengthUnitOf(0.01f))
        assertEquals(LengthUnit.IN, lengthUnitOf(0.0254f))
        assertEquals(null, lengthUnitOf(0.1f))
        assertEquals(null, lengthUnitOf(null))
    }
}
