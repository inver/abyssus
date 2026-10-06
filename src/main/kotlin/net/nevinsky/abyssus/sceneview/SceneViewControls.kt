/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.scene.SceneContent
import net.nevinsky.abyssus.editor.content.CameraPlacement
import net.nevinsky.abyssus.editor.pick.GizmoMode

/** An entry of the camera selector: [id] is the camera entity to look through, null for the free orbit view. */
data class CameraChoice(val id: String?, val label: String) {
    override fun toString() = label
}

/** "Free camera" followed by the scene's cameras by name (their id when unnamed, which [CameraPlacement.name] already is). */
fun cameraChoices(content: SceneContent, freeLabel: String): List<CameraChoice> =
    listOf(CameraChoice(null, freeLabel)) + content.cameras.map { CameraChoice(it.entityId, it.name) }

/** What the Scene view's toolbar allows; the panel copies it onto its buttons. */
data class ToolbarState(
    val moveSelected: Boolean,
    val rotateSelected: Boolean,
    val gizmoEnabled: Boolean,
    val cameraEnabled: Boolean,
    val dropEnabled: Boolean,
    val addLightEnabled: Boolean,
    val addAssetEnabled: Boolean,
)

/**
 * The toolbar for gizmo [mode]: editing is possible when neither the ray experiment nor Play runs; Drop needs a
 * droppable selection ([canDrop]), and Add Light / Add Asset their choices ([canAddLight], [canAddAsset]).
 */
fun toolbarState(
    mode: GizmoMode,
    experimenting: Boolean,
    playing: Boolean,
    canDrop: Boolean,
    canAddLight: Boolean,
    canAddAsset: Boolean,
): ToolbarState {
    val editing = !experimenting && !playing
    return ToolbarState(
        moveSelected = mode == GizmoMode.MOVE,
        rotateSelected = mode == GizmoMode.ROTATE,
        gizmoEnabled = editing,
        cameraEnabled = !experimenting,
        dropEnabled = editing && canDrop,
        addLightEnabled = editing && canAddLight,
        addAssetEnabled = editing && canAddAsset,
    )
}

/** Which Play controls are enabled in [phase]; Stop is enabled while the simulation is [active]. */
data class PlayControls(val play: Boolean, val pause: Boolean, val step: Boolean, val stop: Boolean)

fun playControls(phase: PlayState.Phase, active: Boolean): PlayControls = PlayControls(
    play = phase == PlayState.Phase.IDLE || phase == PlayState.Phase.FAILED || phase == PlayState.Phase.PAUSED,
    pause = phase == PlayState.Phase.PLAYING,
    step = phase == PlayState.Phase.PAUSED,
    stop = active,
)
