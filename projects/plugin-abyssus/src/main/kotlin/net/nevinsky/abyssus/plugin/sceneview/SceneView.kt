/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.scene.AssetRevisionBatch
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
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

    /**
     * Called on the AWT thread when a drawing is finished: the model, the points on the terrain and whether the line is
     * closed. Returns whether the arrangement was written.
     */
    var onArrangementCreate: ((RenderAsset, List<Vec3>, Boolean) -> Boolean)?
        get() = null
        set(_) {}

    /** Called when a path point was dragged, inserted or removed, with the arrangement's id and its new points. Returns whether it was written. */
    var onArrangementEdit: ((String, List<Vec3>) -> Boolean)?
        get() = null
        set(_) {}

    /** Called when the user asks to regenerate the arrangement with this id. */
    var onRegenerate: ((String) -> Unit)?
        get() = null
        set(_) {}

    /** Puts the view in drawing mode for a new path arrangement of the model [asset]. */
    fun startDrawing(asset: RenderAsset) {}

    /** The ground under the scene as the view drew it, or null before the first frame or when the view has none. */
    fun groundHeights(): GroundHeight? = null

    /** Selects an entity after creation or a selection in the Abyssus tree. */
    fun selectEntity(entityId: String) {}

    fun setParams(params: SceneRenderParams)

    /** Loads the assets of [revision] again once the view can safely draw; a hidden view keeps it until it is shown. */
    fun refreshAssets(revision: AssetRevisionBatch) {}

    /** The scene's document is about to change: a running simulation stops first. */
    fun stopPlay() {}

    /** Where a new object is placed (the point the camera orbits around); null when the view places nothing. */
    fun placementPoint(): Vec3? = null

    /**
     * Rendering cannot continue: shows [message] in place of the viewport and keeps whatever else the view has (its toolbar,
     * disabled). True when the view did that; false (the default) leaves the editor to show the message in place of the view.
     */
    fun showUnavailable(message: String): Boolean = false

    /** Whether the view is in Play, when nothing may be added to its scene. */
    val playing: Boolean get() = false
}
