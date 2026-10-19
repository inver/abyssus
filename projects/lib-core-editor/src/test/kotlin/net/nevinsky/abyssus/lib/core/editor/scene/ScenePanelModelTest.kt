/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePanelModelTest {
    private fun model(json: String, inProject: Boolean) = scenePanelModel(SceneJson().parse(json), inProject)

    @Test
    fun projectSceneOffersBrowseAndShowsTheSavedSkybox() {
        val row = model("""{"skyboxName":"skybox_default"}""", inProject = true).skybox
        assertEquals("skybox_default", row.value)
        assertTrue(row.browsable)
    }

    @Test
    fun standaloneSceneHasTheFieldButNoBrowse() {
        val row = model("""{"skyboxName":"x"}""", inProject = false).skybox
        assertEquals("x", row.value)
        assertFalse(row.browsable)
    }

    @Test
    fun nullAbsentAndNonTextSkyboxesAreNone() {
        for (json in listOf("""{"skyboxName":null}""", "{}", """{"skyboxName":5}""")) assertNull(json, model(json, true).skybox.value)
    }
}
