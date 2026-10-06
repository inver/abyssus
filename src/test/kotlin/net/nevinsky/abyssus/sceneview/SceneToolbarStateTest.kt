/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.scene.SceneContent
import net.nevinsky.abyssus.editor.content.CameraPlacement
import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.pick.GizmoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Scene view toolbar's rules without Swing: camera choices and which controls are enabled. */
class SceneToolbarStateTest {
    @Test fun freeCameraComesFirstThenTheSceneCamerasByName() {
        val content = SceneContent(cameras = listOf(
            CameraPlacement("4", "Main", Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f), null),
            CameraPlacement("9", "9", Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f), null),
        ))
        assertEquals(
            listOf(CameraChoice(null, "Free"), CameraChoice("4", "Main"), CameraChoice("9", "9")),
            cameraChoices(content, "Free"),
        )
    }

    @Test fun editingNeedsNeitherPlayNorTheExperiment() {
        val editing = toolbarState(GizmoMode.ROTATE, experimenting = false, playing = false, canDrop = true, canAddLight = true, canAddAsset = false)
        assertEquals(ToolbarState(false, true, true, true, true, true, false), editing)
        val playing = toolbarState(GizmoMode.MOVE, experimenting = false, playing = true, canDrop = true, canAddLight = true, canAddAsset = true)
        assertFalse(playing.gizmoEnabled || playing.dropEnabled || playing.addLightEnabled || playing.addAssetEnabled)
        assertTrue(playing.cameraEnabled)
        val experimenting = toolbarState(GizmoMode.MOVE, experimenting = true, playing = false, canDrop = true, canAddLight = true, canAddAsset = true)
        assertFalse(experimenting.cameraEnabled || experimenting.gizmoEnabled)
    }

    @Test fun playControlsFollowThePhase() {
        assertEquals(PlayControls(play = true, pause = false, step = false, stop = false), playControls(PlayState.Phase.IDLE, active = false))
        assertEquals(PlayControls(play = false, pause = true, step = false, stop = true), playControls(PlayState.Phase.PLAYING, active = true))
        assertEquals(PlayControls(play = true, pause = false, step = true, stop = true), playControls(PlayState.Phase.PAUSED, active = true))
        assertEquals(PlayControls(play = false, pause = false, step = false, stop = true), playControls(PlayState.Phase.STARTING, active = true))
        assertEquals(PlayControls(play = true, pause = false, step = false, stop = false), playControls(PlayState.Phase.FAILED, active = false))
    }
}
