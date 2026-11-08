/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.editor.content.Pose

/** Which terrain brush a [BrushMode] holds; each kind has its own [BrushStroke]. */
enum class BrushKind { FOLIAGE, SCULPT, SPLAT }

/** The kind of the Paint Foliage brush in [BrushMode.kind]. */
val FOLIAGE_BRUSH = BrushKind.FOLIAGE

/** The kind of the Sculpt Terrain brush in [BrushMode.kind]. */
val SCULPT_BRUSH = BrushKind.SCULPT

/** The kind of the Paint Texture brush in [BrushMode.kind]. */
val SPLAT_BRUSH = BrushKind.SPLAT

/**
 * What a brush kind keeps beside the shared fields of its [BrushMode] (the foliage layer, say); nothing reads it but
 * the kind itself.
 */
interface BrushOptions

/**
 * The terrain brush mode of a view: the brush [kind] (which [BrushStroke] strokes), the terrain [entityId] (the
 * selected entity) it works on, the brush [radius] in world units, the [strength] from 0 through 1, and whether the
 * brush is [reversed] (erasing, or an inverted tool; the strip resolves it, Shift included). One mode holds at a time.
 */
data class BrushMode(
    val kind: BrushKind,
    val entityId: Long,
    val radius: Float,
    val strength: Float,
    val reversed: Boolean = false,
    val options: BrushOptions? = null,
)

/** A hinge deflection of [degrees] previewed on part [node] of entity [entityId] in one view. */
data class HingePreview(val entityId: Long, val node: String, val degrees: Float)

/** What the view has selected: an entity of the scene. */
sealed interface SceneTarget {
    data class Entity(val id: Long) : SceneTarget
}

/**
 * What the left button does in the view, read from the fields that hold the modes. The precedence lives here and
 * nowhere else: drawing beats editing a path's points, which beats a terrain brush, which beats plain selection.
 */
sealed interface ViewMode {
    data object Select : ViewMode

    data class Brush(val brush: BrushMode) : ViewMode

    data class PathEditing(val entityId: Long, val point: Int?) : ViewMode

    data class Drawing(val drawing: PathDrawing) : ViewMode
}

/**
 * What the user has done in the scene view, apart from the scene itself: the selection, the gizmo and what it is
 * hovering, the camera entity looked through, and the transforms previewed during a drag or after a drop. The view
 * panel changes it, the renderer draws from it and the picking queries read it; all on the AWT thread.
 */
class SceneViewState {
    /** What the view highlights and shows a gizmo on, or null. */
    var selection: SceneTarget? = null
        set(value) {
            field = value
            clearPart()
        }

    /**
     * The entity id the view highlights and shows a gizmo on, or null. Setting it is a plain entity selection: it
     * clears the part selection, Edit Pivot and the hinge preview (use [selectPart] to select a part).
     */
    var selectedId: Long?
        get() = (selection as? SceneTarget.Entity)?.id
        set(value) {
            selection = value?.let(SceneTarget::Entity)
        }

    /** The node of the selected entity's model that is selected as a part, or null (OpenSpec `model-parts`). */
    var selectedPart: String? = null
        private set

    /** Whether the gizmo drags the selected part's pivot frame instead of the entity; off when a view opens. */
    var editPivot: Boolean = false

    /** How a click on the selected part sets its pivot while [editPivot] is on. */
    var pivotSnap: PivotSnap = PivotSnap.NONE

    /** The world frame of the pivot being dragged (shown instead of the file's), or null. */
    var pivotPreview: Matrix4? = null

    /** The hinge deflection previewed in this view only (never written), or null. */
    var hingePreview: HingePreview? = null

    /** Selects part [node] of model entity [entityId], which becomes the selected entity. */
    fun selectPart(entityId: Long, node: String) {
        selection = SceneTarget.Entity(entityId)
        selectedPart = node
    }

    /** Drops the part selection and what depends on it. */
    fun clearPart() {
        selectedPart = null
        editPivot = false
        pivotSnap = PivotSnap.NONE
        pivotPreview = null
        hingePreview = null
    }

    var gizmoMode: GizmoMode = GizmoMode.MOVE

    /** Whether the gizmo follows the world axes or the selected object's own. */
    var gizmoSpace: GizmoSpace = GizmoSpace.WORLD

    /** Snapping of gizmo drags, or null while it is off. */
    var snap: GizmoSnap? = null

    /** The gizmo handle under the cursor, drawn brighter. */
    var hoveredAxis: GizmoAxis? = null

    /** The plane handle under the cursor, drawn brighter. */
    var hoveredPlane: GizmoPlane? = null

    /** The camera entity the viewport renders from instead of the orbit view, or null. */
    var viewCamera: Long? = null

    /**
     * Transforms shown over the scene's own while a gizmo drag or completed drop is previewed (entity id to where it is
     * now).
     */
    var preview: Map<Long, DragResult> = emptyMap()

    /** Simulated poses shown instead of the authored placements while playing (entity id to pose); empty otherwise. */
    var poses: Map<Long, Pose> = emptyMap()

    /** Whether every dummy's marker is drawn and pickable ("Show Dummies"); off when a view opens. */
    var showDummies: Boolean = false

    /** Whether gizmos are offered: off while a simulation plays. */
    var gizmosEnabled: Boolean = true

    /** The cloud technique this view draws in place of the sky asset's own, or null for the asset's. */
    var cloudTechnique: CloudTechnique? = null

    /**
      * The terrain brush mode this view is in, or null; gizmos are hidden and the left button strokes the terrain while
      * it
     * is set. Selecting another entity (or nothing) ends it. Only one brush holds at a time.
     */
    var brush: BrushMode? = null

    /** Whether a terrain brush is on the view. */
    val brushing: Boolean get() = brush != null

    /** Ends the terrain brush on the view. */
    fun endBrush() {
        brush = null
    }

    /**
     * The path arrangement being drawn (the model and the points clicked so far), or null; the left click adds points.
     */
    var drawing: PathDrawing? = null

    /**
     * The points of an arrangement while a handle is dragged or just dropped, shown over the file's; null otherwise.
     */
    var pathEdit: PathEdit? = null

    /**
     * The points of [pathEditing] edited so far and not yet written; Apply writes them, Close drops them. Null before
     * the first change.
     */
    var pathDraft: PathEdit? = null

    /**
     * The arrangement whose control points are being edited (Edit in the Properties panel until Apply or Close), or
     * null.
     */
    var pathEditing: Long? = null

    /** The control point of [pathEditing] that wears the move gizmo, or null. */
    var selectedPathPoint: Int? = null

    /** The mode the view is in; see [ViewMode] for which of the independent fields wins. */
    val mode: ViewMode
        get() {
            drawing?.let { return ViewMode.Drawing(it) }
            pathEditing?.let { return ViewMode.PathEditing(it, selectedPathPoint) }
            brush?.let { return ViewMode.Brush(it) }
            return ViewMode.Select
        }
}
