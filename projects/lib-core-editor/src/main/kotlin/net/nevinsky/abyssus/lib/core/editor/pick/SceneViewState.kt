/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import net.nevinsky.abyssus.lib.core.editor.content.Pose

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
}
