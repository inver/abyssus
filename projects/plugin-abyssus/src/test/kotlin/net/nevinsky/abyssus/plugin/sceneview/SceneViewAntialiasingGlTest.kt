/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import java.awt.Component
import java.awt.Container
import javax.swing.JFrame

/** Real default framebuffer and context churn; opt-in with -Dabyssus.glTests=true and a display. */
class SceneViewAntialiasingGlTest : BasePlatformTestCase() {
    private fun canvas(c: Component): GuardedGLCanvas? =
        (c as? GuardedGLCanvas) ?: (c as? Container)?.components?.firstNotNullOfOrNull { canvas(it) }

    private fun awaitFrames(panel: SceneViewPanel, failures: List<Throwable>) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < deadline && !Regex("^\\d+ fps").containsMatchIn(panel.statsText()) && failures.isEmpty()) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(20)
        }
        assertTrue("render failures: $failures", failures.isEmpty())
        assertTrue("new context must present frames: ${panel.statsText()}", Regex("^\\d+ fps").containsMatchIn(panel.statsText()))
    }

    private fun samples(panel: SceneViewPanel): Int {
        val canvas = canvas(panel)!!
        assertTrue(canvas.glSafe())
        return canvas.executeInContext {
            GL.createCapabilities()
            GL11.glGetInteger(GL13.GL_SAMPLES)
        }
    }

    private fun withPanel(level: AntialiasingLevel, body: (SceneViewPanel, List<Throwable>) -> Unit) {
        val failures = mutableListOf<Throwable>()
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer(), antialiasingLevel = level)
        panel.onFailure = { failures += it }
        panel.setFpsEnabled(true)
        val window = JFrame().apply {
            setSize(800, 600)
            contentPane.add(panel)
            isVisible = true
        }
        try {
            awaitFrames(panel, failures)
            body(panel, failures)
        } finally {
            panel.dispose()
            window.dispose()
        }
    }

    fun testFourSamplesAndLiveChangesKeepRenderingAndSelection() {
        if (!GlHarness.enabled) return
        withPanel(AntialiasingLevel.X4) { panel, failures ->
            val count = samples(panel)
            if (panel.effectiveSampleCount > 0) assertTrue("multisampled format: $count", count >= 1)
            else assertEquals(0, count) // no supported multisampled pixel format on this machine
            panel.selectEntity("selected")
            panel.orbit.lookAlong(Vector3(1f, 0f, 0f))
            val target = panel.orbit.target
            val original = canvas(panel)
            panel.setAntialiasing(AntialiasingLevel.OFF)
            awaitFrames(panel, failures)
            assertNotSame(original, canvas(panel))
            assertEquals(0, samples(panel))
            assertEquals(target, panel.orbit.target)
            // Also exercise hiding without removeNotify (as with background editor cards).
            panel.isVisible = false
            val hidden = canvas(panel)
            panel.setAntialiasing(AntialiasingLevel.X8)
            assertSame(hidden, canvas(panel))
            panel.isVisible = true
            awaitFrames(panel, failures)
            assertNotSame(hidden, canvas(panel))
            val after = samples(panel)
            if (panel.effectiveSampleCount > 0) assertTrue(after >= 1) else assertEquals(0, after)
        }
    }

    fun testExplicitOffHasNoSamples() {
        if (!GlHarness.enabled) return
        withPanel(AntialiasingLevel.OFF) { panel, _ -> assertEquals(0, samples(panel)) }
    }
}
