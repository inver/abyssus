/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import javax.swing.JFrame

/** A real panel in a real window: the frame rate must become a number through the actual render loop. Opt-in: `-Dabyssus.glTests=true`. */
class SceneViewPanelFpsGlTest : BasePlatformTestCase() {
    /** Lets the Swing timers and paints run (this test already is the EDT) for [millis]. */
    private fun pump(millis: Long) {
        val end = System.nanoTime() + millis * 1_000_000L
        while (System.nanoTime() < end) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(20)
        }
    }

    fun testTheFrameRateBecomesANumberWhileTheSettingIsOnAndGoesWhenItIsOff() {
        if (!GlHarness.enabled) return // opt-in: -Dabyssus.glTests=true and a display
        var shown = true
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer(), showFps = { shown })
        val window = JFrame().apply {
            setSize(800, 600)
            contentPane.add(panel)
            isVisible = true
        }
        try {
            val numeric = Regex("^\\d+ fps")
            var text = ""
            val deadline = System.nanoTime() + 25_000_000_000L
            while (System.nanoTime() < deadline && !numeric.containsMatchIn(text)) {
                pump(250)
                text = panel.statsText()
            }
            assertTrue("a measured rate leads the strip, was '$text'", numeric.containsMatchIn(text))
            shown = false
            pump(500)
            assertFalse(panel.statsText(), panel.statsText().contains("fps"))
        } finally {
            window.dispose()
            panel.dispose()
        }
    }
}
