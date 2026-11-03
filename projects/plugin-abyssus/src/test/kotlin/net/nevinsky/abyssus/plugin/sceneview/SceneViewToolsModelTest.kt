/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.pick.GizmoMode
import net.nevinsky.abyssus.lib.core.editor.pick.GizmoSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The view's tool choices and the toolbar states derived from them, without Swing or GL. */
class SceneViewToolsModelTest {
    private fun state(
        mode: GizmoMode = GizmoMode.MOVE,
        playing: Boolean = false,
        experimenting: Boolean = false,
        display: ViewDisplay = ViewDisplay(),
        shadowCasters: Boolean = true,
        fog: Boolean = true,
        throughCamera: Boolean = false,
    ) = toolbarState(mode, experimenting, playing, true, true, true, display, shadowCasters, fog, throughCamera)

    @Test fun aNewViewStartsInWorldWithSnapAndGridOffAndShadowsAndFogOn() {
        val m = SceneViewToolsModel()
        assertEquals(GizmoSpace.WORLD, m.space)
        assertFalse(m.snapOn)
        assertEquals(DEFAULT_SNAP_STEP, m.snapStep, 0f)
        assertEquals(ViewDisplay(shadows = true, fog = true, grid = false, shading = Shading.LIT), m.display)
    }

    @Test fun onlyRealChangesNotifyTheListener() {
        val m = SceneViewToolsModel()
        var calls = 0
        m.onChanged = { calls++ }
        m.space = GizmoSpace.WORLD
        m.setGrid(false)
        assertEquals(0, calls)
        m.space = GizmoSpace.LOCAL
        m.snapOn = true
        m.setGrid(true)
        m.setShading(Shading.UNLIT)
        assertEquals(4, calls)
    }

    @Test fun theSnapStepMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { SceneViewToolsModel().snapStep = 0f }
    }

    @Test fun unlitSwitchesTheShadowPassOffWithoutForgettingTheToggle() {
        val m = SceneViewToolsModel()
        m.setShading(Shading.UNLIT)
        assertFalse(m.display.drawsShadows)
        assertTrue("the toggle itself stays on", m.display.shadows)
        m.setShading(Shading.LIT)
        assertTrue(m.display.drawsShadows)
    }

    @Test fun wireframeIsInTheShadingEntriesAndDrawsNoShadows() {
        assertTrue(Shading.entries.contains(Shading.WIREFRAME))
        val m = SceneViewToolsModel()
        m.setShading(Shading.WIREFRAME)
        assertFalse(m.display.drawsShadows)
        assertTrue("the toggle itself stays on", m.display.shadows)
    }

    @Test fun selectIsPressedInSelectMode() {
        val s = state(GizmoMode.SELECT)
        assertTrue(s.selectSelected)
        assertFalse(s.moveSelected || s.rotateSelected)
    }

    @Test fun playingDisablesEditingButNotTheDisplayControls() {
        val s = state(playing = true)
        assertFalse(s.gizmoEnabled || s.spaceEnabled || s.snapEnabled || s.dropEnabled || s.addLightEnabled || s.addAssetEnabled)
        assertTrue(s.shadowsEnabled && s.fogEnabled && s.gridEnabled && s.shadingEnabled)
        assertFalse("Frame selection does nothing while playing", s.frameEnabled)
    }

    @Test fun theExperimentDisablesTheCameraAndEditing() {
        val s = state(experimenting = true)
        assertFalse(s.cameraEnabled || s.gizmoEnabled || s.snapEnabled || s.frameEnabled)
    }

    @Test fun shadowsNeedACasterAndALitView() {
        assertFalse(state(shadowCasters = false).shadowsEnabled)
        assertFalse(state(display = ViewDisplay(shading = Shading.UNLIT)).shadowsEnabled)
        assertTrue(state().shadowsEnabled)
    }

    @Test fun fogNeedsASceneThatUsesIt() {
        assertFalse(state(fog = false).fogEnabled)
        assertTrue(state().fogEnabled)
    }

    @Test fun frameIsOffWhileLookingThroughASceneCamera() {
        assertFalse(state(throughCamera = true).frameEnabled)
    }
}
