/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.SceneFileEditorProvider

/** The real provider, editor and panel together: `Show FPS` must reach the strip's text. */
class SceneEditorFpsTest : BasePlatformTestCase() {
    private val key = "net.nevinsky.abyssus.sceneView.showFps"

    override fun tearDown() {
        try {
            PropertiesComponent.getInstance(project).unsetValue(key)
        } finally {
            super.tearDown()
        }
    }

    private fun open(): Pair<SceneFileEditor, SceneViewPanel?> {
        val file = myFixture.addFileToProject("fps/Main.scene", """{"format":"abyssus","formatVersion":1,"name":"x"}""").virtualFile
        val editor = SceneFileEditorProvider().createEditor(project, file) as SceneFileEditor
        return editor to (editor.sceneView as? SceneViewPanel)
    }

    fun testTheSavedPreferenceReachesARealPanel() {
        project.service<SceneViewSettings>().showFps = true
        val (editor, panel) = open()
        try {
            assertNotNull("the editor made a real panel", panel)
            assertTrue(panel!!.statsText(), panel.statsText().startsWith("-- fps"))
        } finally { editor.dispose() }
    }

    fun testTurningItOnLaterReachesAnOpenRealPanel() {
        val (editor, panel) = open()
        try {
            assertNotNull(panel)
            assertFalse(panel!!.statsText().contains("fps"))
            project.service<SceneViewSettings>().showFps = true
            assertTrue(panel.statsText(), panel.statsText().startsWith("-- fps"))
            project.service<SceneViewSettings>().showFps = false
            assertFalse(panel.statsText().contains("fps"))
        } finally { editor.dispose() }
    }
}
