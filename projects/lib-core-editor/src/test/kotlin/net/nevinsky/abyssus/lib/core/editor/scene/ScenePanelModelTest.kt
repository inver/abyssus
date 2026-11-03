/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.editor.parseScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePanelModelTest {
    private fun model(json: String, inProject: Boolean) = scenePanelModel(parseScene(json), inProject)

    @Test
    fun projectSceneOffersBrowseAndShowsTheSavedSkybox() {
        val row = model("""{"format":"abyssus","formatVersion":1,"skyboxName":"skybox_default"}""", inProject = true).skybox
        assertEquals("skybox_default", row.value)
        assertTrue(row.browsable)
    }

    @Test
    fun standaloneSceneHasTheFieldButNoBrowse() {
        val row = model("""{"format":"abyssus","formatVersion":1,"skyboxName":"x"}""", inProject = false).skybox
        assertEquals("x", row.value)
        assertFalse(row.browsable)
    }

    @Test
    fun nullAbsentAndNonTextSkyboxesAreNone() {
        for (body in listOf("\"skyboxName\":null", "")) {
            val json = """{"format":"abyssus","formatVersion":1${if (body.isEmpty()) "" else ",$body"}}"""
            assertNull(json, model(json, true).skybox.value)
        }
    }
}
