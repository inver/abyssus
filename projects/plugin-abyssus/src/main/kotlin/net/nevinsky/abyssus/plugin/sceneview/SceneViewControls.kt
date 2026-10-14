/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.pick.GizmoMode

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
    val selectSelected: Boolean = false,
    /** Local/World and Snap: editing controls, off while Play or the ray experiment runs. */
    val spaceEnabled: Boolean = gizmoEnabled,
    val snapEnabled: Boolean = gizmoEnabled,
    /** The view toggles stay usable while playing; Shadows and Fog only when the scene has something for them. */
    val shadowsEnabled: Boolean = true,
    val fogEnabled: Boolean = true,
    val gridEnabled: Boolean = true,
    val shadingEnabled: Boolean = true,
    val frameEnabled: Boolean = true,
)

/** This state with every control disabled, as when the view cannot render; what is pressed stays pressed. */
fun ToolbarState.disabled(): ToolbarState = copy(
    gizmoEnabled = false, cameraEnabled = false, dropEnabled = false, addLightEnabled = false, addAssetEnabled = false,
    spaceEnabled = false, snapEnabled = false, shadowsEnabled = false, fogEnabled = false, gridEnabled = false,
    shadingEnabled = false, frameEnabled = false,
)

/**
 * The toolbar for gizmo [mode]: editing is possible when neither the ray experiment nor Play runs; Drop needs a
 * droppable selection ([canDrop]), and Add Light / Add Asset their choices ([canAddLight], [canAddAsset]). Shadows
 * needs a shadow-casting light ([hasShadowCasters]) and a Lit view, Fog a scene that uses fog ([sceneUsesFog]); Frame
 * selection is off while looking through a scene camera ([lookingThroughCamera]) or playing.
 */
fun toolbarState(
    mode: GizmoMode,
    experimenting: Boolean,
    playing: Boolean,
    canDrop: Boolean,
    canAddLight: Boolean,
    canAddAsset: Boolean,
    display: ViewDisplay = ViewDisplay(),
    hasShadowCasters: Boolean = true,
    sceneUsesFog: Boolean = true,
    lookingThroughCamera: Boolean = false,
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
        selectSelected = mode == GizmoMode.SELECT,
        spaceEnabled = editing,
        snapEnabled = editing,
        shadowsEnabled = hasShadowCasters && display.shading == Shading.LIT,
        fogEnabled = sceneUsesFog,
        gridEnabled = true,
        shadingEnabled = true,
        frameEnabled = !playing && !experimenting && !lookingThroughCamera,
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
