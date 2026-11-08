/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams

/** OpenSpec `scene-view-toolbar`, "Show Dummies toggle". */
class SceneViewToolbarTest : BasePlatformTestCase() {
    private fun named(c: Component, name: String): Component? =
        if (c.name == name) {
            c
        } else {
            (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }
        }

    private fun showDummies(panel: SceneViewPanel): ToggleAction =
        (named(panel, "AbyssusSceneDisplay") as ActionToolbar).actions
            .single { it.templatePresentation.text == "Show Dummies" } as ToggleAction

    fun testShowDummiesIsOffWhenAViewOpens() {
        val renderer = testRenderer()
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer)
        try {
            val toggle = showDummies(panel)
            assertFalse(toggle.isSelected(TestActionEvent.createTestEvent(toggle)))
            assertFalse(panel.tools.display.dummies)
            assertFalse(renderer.state.showDummies)
        } finally {
            panel.dispose()
        }
    }

    fun testPressingItShowsEveryDummy() {
        val renderer = testRenderer()
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer)
        try {
            val toggle = showDummies(panel)
            toggle.setSelected(TestActionEvent.createTestEvent(toggle), true)
            assertTrue(panel.tools.display.dummies)
            assertTrue("the view draws and picks them", renderer.state.showDummies)
            // the other layers are untouched
            assertTrue(panel.tools.display.shadows && panel.tools.display.fog && !panel.tools.display.grid)
        } finally {
            panel.dispose()
        }
    }
}
