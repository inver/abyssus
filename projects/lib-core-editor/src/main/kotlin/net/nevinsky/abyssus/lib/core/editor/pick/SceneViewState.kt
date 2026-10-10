/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.editor.content.Pose

/**
 * The Paint Foliage mode of a view: the terrain [entityId] (the selected entity) whose layer [layerId] mask is
 * painted, the brush [radius] in world units, the [strength] from 0 through 1, and whether the brush [erase]s
 * instead of painting (the strip's choice, flipped while Shift is held).
 */
data class FoliagePaintMode(
    val entityId: String,
    val layerId: Int,
    val radius: Float,
    val strength: Float,
    val erase: Boolean = false,
)

/**
 * What the user has done in the scene view, apart from the scene itself: the selection, the gizmo and what it is
 * hovering, the camera entity looked through, and the transforms previewed during a drag or after a drop. The view
 * panel changes it, the renderer draws from it and the picking queries read it; all on the AWT thread.
 */
class SceneViewState {
    /** The entity id the view highlights and shows a gizmo on, or null. */
    var selectedId: String? = null

    var gizmoMode: GizmoMode = GizmoMode.MOVE

    /** The gizmo handle under the cursor, drawn brighter. */
    var hoveredAxis: GizmoAxis? = null

    /** The camera entity the viewport renders from instead of the orbit view, or null. */
    var viewCamera: String? = null

    /** Transforms shown over the scene's own while a gizmo drag or completed drop is previewed (entity id to where it is now). */
    var preview: Map<String, DragResult> = emptyMap()

    /** Simulated poses shown instead of the authored placements while playing (entity id to pose); empty otherwise. */
    var poses: Map<String, Pose> = emptyMap()

    /** Whether gizmos are offered: off while a simulation plays. */
    var gizmosEnabled: Boolean = true

    /** The cloud technique this view draws in place of the sky asset's own, or null for the asset's. */
    var cloudTechnique: CloudTechnique? = null

    /**
     * The Paint Foliage mode this view is in, or null; gizmos are hidden and the left button paints while it is set.
     * Selecting another entity (or nothing) ends it.
     */
    var paint: FoliagePaintMode? = null
}
