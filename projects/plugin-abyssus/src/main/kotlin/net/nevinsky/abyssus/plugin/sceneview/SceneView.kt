/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.scene.AssetRevisionBatch
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import com.intellij.openapi.Disposable
import javax.swing.JComponent

/** A live view of a scene. The editor talks to this so the GL-backed [SceneViewPanel] can be replaced in tests. */
interface SceneView : Disposable {
    val view: JComponent

    /** Called on the AWT thread when rendering fails and the view has stopped. */
    var onFailure: ((Throwable) -> Unit)?

    /** Called on the AWT thread with the id (key under `ecs/entities`) of the entity the user clicked. */
    var onPick: ((String) -> Unit)?

    /**
     * Called on the AWT thread when a drag of a gizmo handle ends with the entity moved or rotated, with the entity's id
     * (key under `ecs/entities`) and the change. Returns whether it was written to the scene.
     */
    var onTransform: ((String, TransformEdit) -> Boolean)?

    /** Selects an entity after creation or a selection in the Abyssus tree. */
    fun selectEntity(entityId: String) {}

    fun setParams(params: SceneRenderParams)

    /** Loads the assets of [revision] again once the view can safely draw; a hidden view keeps it until it is shown. */
    fun refreshAssets(revision: AssetRevisionBatch) {}

    /** The scene's document is about to change: a running simulation stops first. */
    fun stopPlay() {}

    /** Where a new object is placed (the point the camera orbits around); null when the view places nothing. */
    fun placementPoint(): Vec3? = null

    /** Whether the view is in Play, when nothing may be added to its scene. */
    val playing: Boolean get() = false
}
