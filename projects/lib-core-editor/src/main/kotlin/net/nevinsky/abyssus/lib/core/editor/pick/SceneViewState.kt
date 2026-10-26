/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.editor.content.Pose

/** The kind of the Paint Foliage brush in [BrushMode.kind]. */
const val FOLIAGE_BRUSH = "foliage"

/** The kind of the Sculpt Terrain brush in [BrushMode.kind]. */
const val SCULPT_BRUSH = "sculpt"

/** The kind of the Paint Texture brush in [BrushMode.kind]. */
const val SPLAT_BRUSH = "splat"

/** What a brush kind keeps beside the shared fields of its [BrushMode] (the foliage layer, say); nothing reads it but the kind itself. */
interface BrushOptions

/**
 * The terrain brush mode of a view: the brush [kind] (which [BrushStroke] strokes), the terrain [entityId] (the
 * selected entity) it works on, the brush [radius] in world units, the [strength] from 0 through 1, and whether the
 * brush is [reversed] (erasing, or an inverted tool; the strip resolves it, Shift included). One mode holds at a time.
 */
data class BrushMode(
    val kind: String,
    val entityId: String,
    val radius: Float,
    val strength: Float,
    val reversed: Boolean = false,
    val options: BrushOptions? = null,
)

/** What the view has selected: an entity of the scene, or a water surface (separate namespaces, so an id is never confused). */
sealed interface SceneTarget {
    data class Entity(val id: String) : SceneTarget

    data class Water(val id: String) : SceneTarget
}

/**
 * What the user has done in the scene view, apart from the scene itself: the selection, the gizmo and what it is
 * hovering, the camera entity looked through, and the transforms previewed during a drag or after a drop. The view
 * panel changes it, the renderer draws from it and the picking queries read it; all on the AWT thread.
 */
class SceneViewState {
    /** What the view highlights and shows a gizmo on, or null. */
    var selection: SceneTarget? = null

    /** The entity id the view highlights and shows a gizmo on, or null (also when a water surface is selected). */
    var selectedId: String?
        get() = (selection as? SceneTarget.Entity)?.id
        set(value) {
            selection = value?.let(SceneTarget::Entity)
        }

    /** The water surface the view highlights and shows a move gizmo on, or null. */
    var selectedWater: String?
        get() = (selection as? SceneTarget.Water)?.id
        set(value) {
            selection = value?.let(SceneTarget::Water)
        }

    /** The new centre of each water surface while it is dragged or just dropped, shown over the file's (water id to where it is now). */
    var waterPreview: Map<String, Vector3> = emptyMap()

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
     * The terrain brush mode this view is in, or null; gizmos are hidden and the left button strokes the terrain while it
     * is set. Selecting another entity (or nothing) ends it. Only one brush holds at a time.
     */
    var brush: BrushMode? = null

    /** Whether a terrain brush is on the view. */
    val brushing: Boolean get() = brush != null

    /** Ends the terrain brush on the view. */
    fun endBrush() {
        brush = null
    }

    /** The path arrangement being drawn (the model and the points clicked so far), or null; the left click adds points. */
    var drawing: PathDrawing? = null

    /** The points of an arrangement while a handle is dragged or just dropped, shown over the file's; null otherwise. */
    var pathEdit: PathEdit? = null

    /** The points of [pathEditing] edited so far and not yet written; Apply writes them, Close drops them. Null before the first change. */
    var pathDraft: PathEdit? = null

    /** The arrangement whose control points are being edited (Edit in the Properties panel until Apply or Close), or null. */
    var pathEditing: String? = null

    /** The control point of [pathEditing] that wears the move gizmo, or null. */
    var selectedPathPoint: Int? = null
}
