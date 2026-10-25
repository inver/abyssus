/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import org.junit.Assert.*

class SceneViewPanelAntialiasingTest : BasePlatformTestCase() {
    fun testRefusedFormatsRetryDownToOffAndWarnOnlyOncePerView() {
        val replacements = mutableListOf<Int>()
        val warnings = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()
        val state = CanvasAntialiasing(AntialiasingLevel.X8, replacements::add, { samples, _ -> warnings += samples }, failures::add)
        val error = IllegalArgumentException("pixel format refused")
        state.renderFailed(error, initialized = false)
        assertEquals(listOf(4), replacements)
        assertEquals(4, state.samples)
        state.renderFailed(error, initialized = false)
        state.renderFailed(error, initialized = false)
        assertEquals(listOf(4, 2, 0), replacements)
        assertEquals(listOf(8), warnings)
        assertTrue(failures.isEmpty())
        assertTrue(state.swapControl)
        state.renderFailed(error, initialized = false)
        assertFalse("Off is retried once without swap interval", state.swapControl)
        assertEquals(listOf(4, 2, 0, 0), replacements)
        assertTrue(failures.isEmpty())
        state.renderFailed(error, initialized = false)
        assertEquals(listOf(error), failures)
        state.setLevel(AntialiasingLevel.X2, showing = true)
        state.renderFailed(error, initialized = false)
        assertEquals(listOf(8), warnings)
    }

    fun testFailureAfterInitGLIsNotMistakenForRefusedMultisampling() {
        val failures = mutableListOf<Throwable>()
        var replaced = false
        val state = CanvasAntialiasing(AntialiasingLevel.X8, { replaced = true }, { _, _ -> fail("no fallback warning") }, failures::add)
        val error = IllegalStateException("renderer failed")
        state.renderFailed(error, initialized = true)
        assertEquals(listOf(error), failures)
        assertFalse(replaced)
        assertEquals(8, state.samples)
    }

    fun testHiddenChoiceChangesAreCoalescedUntilShowAndFallbackRestartsForANewChoice() {
        val replacements = mutableListOf<Int>()
        val state = CanvasAntialiasing(AntialiasingLevel.X4, replacements::add, { _, _ -> }, { throw it })
        state.setLevel(AntialiasingLevel.OFF, showing = false)
        state.setLevel(AntialiasingLevel.X8, showing = false)
        assertTrue(replacements.isEmpty())
        assertEquals(4, state.samples)
        state.shown()
        assertEquals(listOf(8), replacements)
        state.shown()
        state.setLevel(AntialiasingLevel.X8, showing = true)
        assertEquals(listOf(8), replacements)
        state.renderFailed(IllegalArgumentException(), initialized = false)
        state.setLevel(AntialiasingLevel.X2, showing = true)
        state.renderFailed(IllegalArgumentException(), initialized = false)
        assertEquals(listOf(8, 4, 2, 0), replacements)
    }

    fun testAHiddenPanelDefersCanvasReplacementAndKeepsCpuState() {
        val renderer = testRenderer()
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer, antialiasingLevel = AntialiasingLevel.X4)
        try {
            fun canvas(): java.awt.Component {
                fun find(c: java.awt.Component): java.awt.Component? =
                    if (c is GuardedGLCanvas) c else (c as? java.awt.Container)?.components?.firstNotNullOfOrNull(::find)
                return find(panel)!!
            }
            val original = canvas()
            panel.selectEntity("selected")
            renderer.state.viewCamera = "camera"
            renderer.state.gizmoMode = net.nevinsky.abyssus.lib.core.editor.pick.GizmoMode.ROTATE
            panel.orbit.lookAlong(Vector3(1f, 0f, 0f))
            val target = panel.orbit.target
            panel.setAntialiasing(AntialiasingLevel.OFF)
            assertSame(original, canvas())
            assertEquals(4, panel.effectiveSampleCount)
            assertEquals("selected", renderer.state.selectedId)
            assertEquals("camera", renderer.state.viewCamera)
            assertEquals(net.nevinsky.abyssus.lib.core.editor.pick.GizmoMode.ROTATE, renderer.state.gizmoMode)
            assertEquals(target, panel.orbit.target)
        } finally { panel.dispose() }
    }
}
