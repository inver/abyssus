/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import kotlin.math.abs


/** The size of the view in Swing pixels and of its framebuffer, which is larger on HiDPI screens. */
class ViewSize(val width: Int, val height: Int, val framebufferWidth: Int, val framebufferHeight: Int) {
    val isEmpty: Boolean get() = width <= 0 || height <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0

    fun fx(x: Int): Int = x * framebufferWidth / width

    fun fy(y: Int): Int = y * framebufferHeight / height

    /** The Swing pixel ([x], [y]) in framebuffer pixels. */
    fun toFramebuffer(x: Int, y: Int) = FramebufferPoint(fx(x), fy(y))
}

/** A pixel of the framebuffer. */
data class FramebufferPoint(val x: Int, val y: Int)

/**
 * What the mouse and keys do in the scene view, apart from Swing and GL: selecting by click, orbiting and panning,
 * dragging gizmo handles and cancelling a drag. Mouse positions are Swing pixels of the view described by [size]. The
 * user's choices live in [state]; what is under the cursor and where things would rest come from [queries].
 */
class SceneInteraction(
    private val state: SceneViewState,
    private val queries: SceneQueries,
    private val orbit: OrbitCamera,
) {
    var size = ViewSize(0, 0, 0, 0)

    /** Called with the entity id of a click that selected something. */
    var onPick: ((String) -> Unit)? = null

    /** Called with the id of a click that selected a water surface. */
    var onPickWater: ((String) -> Unit)? = null

    /** Called when a water move ends with the surface at a new centre; returns whether it was written to the scene. */
    var onWaterMove: ((String, Vector3) -> Boolean)? = null

    /** Called when a drag ends with a changed transform; returns whether it was written to the scene. */
    var onTransform: ((String, TransformEdit) -> Boolean)? = null

    /**
     * Called when a drawing is finished with the model, the points on the terrain and whether the line is closed;
     * returns whether the arrangement was written. A refused write keeps the drawing.
     */
    var onArrangementCreate: ((RenderAsset, List<Vector3>, Boolean) -> Boolean)? = null

    /** Called when a point drag, an insert or a removal ends with new points for the arrangement; returns whether it was written. */
    var onArrangementEdit: ((String, List<Vector3>) -> Boolean)? = null

    /** Called when the edit of an arrangement's control points ended because the selection moved on or the arrangement went. */
    var onPathEditEnded: (() -> Unit)? = null

    /** Called when the selection, the mode or the looked-through camera changed by the user or by new scene params. */
    var onStateChanged: (() -> Unit)? = null

    /** Called with the selected entity's id on Copy; the host puts the entity on the clipboard. */
    var onCopy: ((String) -> Unit)? = null

    /** Called on Paste with the point under the cursor; returns whether an entity was added. */
    var onPaste: ((Vector3) -> Boolean)? = null

    /** Called on Delete with the selected entity's id; returns whether it was removed. */
    var onDelete: ((String) -> Boolean)? = null

    /** What paints while the Paint Foliage mode in [state] strokes the terrain, or null when nothing implements it. */
    var foliagePaint: FoliagePaint? = null

    var selectedId: String?
        get() = state.selectedId
        private set(value) {
            state.selectedId = value
        }

    /** The water surface that is selected, or null. */
    val selectedWater: String? get() = state.selectedWater

    /** Selects the water surface [id] (from the tree or after adding it); ends Paint Foliage. */
    fun selectWater(id: String) {
        if (state.selectedWater == id) return
        state.selectedWater = id
        state.paint = null
        stateChanged()
    }

    var mode: GizmoMode
        get() = state.gizmoMode
        set(value) {
            state.gizmoMode = value
            stateChanged()
        }

    var viewCamera: String?
        get() = state.viewCamera
        set(value) {
            state.viewCamera = value
            stateChanged()
        }

    private val click = ClickGesture()
    private var lastX = 0
    private var lastY = 0

    private val host = object : GestureHost {
        override val size: ViewSize get() = this@SceneInteraction.size
        override val foliagePaint: FoliagePaint? get() = this@SceneInteraction.foliagePaint
        override val onTransform: ((String, TransformEdit) -> Boolean)? get() = this@SceneInteraction.onTransform
        override val onWaterMove: ((String, Vector3) -> Boolean)? get() = this@SceneInteraction.onWaterMove
        override val onArrangementEdit: ((String, List<Vector3>) -> Boolean)? get() = this@SceneInteraction.stagedEdit
        override val selectedId: String? get() = this@SceneInteraction.selectedId
        override fun editOf(entityId: String, result: DragResult) = this@SceneInteraction.editOf(entityId, result)
        override fun stateChanged() = this@SceneInteraction.stateChanged()
    }
    private val gestures = GestureDriver(state, queries, host)
    private val drawingSession =
        DrawingSession(state, queries, { size }, { onArrangementCreate }, { stagedEdit }, ::stateChanged)

    val isDragging: Boolean get() = gestures.isDragging

    /** The arrangement being drawn, or null outside the drawing mode. */
    val drawing: PathDrawing? get() = state.drawing

    private var lastDrawnVersion = -1L
    private var lastCanDrop = false
    private var refreshDropAfterFrame = false

    val canDrop: Boolean get() = dropPosition() != null

    private fun dropPosition(): Vector3? {
        val id = selectedId ?: return null
        val content = queries.content
        if (isDragging || id == state.viewCamera || content.terrains.any { it.entityId == id }) return null
        val selected = ScenePreview().selected(content, id) ?: return null
        val lowest = queries.lowestPoint(id) ?: return null
        val height = queries.groundBelow(id) ?: return null
        if (!height.isFinite() || ScenePicker().isResting(lowest, height)) return null
        val p = selected.transform.position
        return Vector3(p.x, (p.y.toDouble() + height.toDouble() - lowest.toDouble()).toFloat(), p.z)
    }

    /** A drop uses the move's preview and write callback, including its rejected-write behaviour. */
    fun drop() {
        val id = selectedId ?: return
        val position = dropPosition() ?: return
        val selected = ScenePreview().selected(queries.content, id) ?: return
        val result = DragResult(selected.transform.copy(position = position), selected.direction)
        state.preview = mapOf(id to result)
        val written = onTransform?.invoke(id, TransformEdit(position = position)) ?: false
        // A synchronous document re-read can clear the preview during the callback. Keep the accepted result
        // until the renderer has another frame; a second key press must use the newly settled box immediately.
        state.preview = if (written) mapOf(id to result) else emptyMap()
        stateChanged()
    }

    /** Where the ground is under the scene, for dropping control points; null when the view has none yet. */
    var groundHeights: (() -> GroundHeight?)? = null

    /**
     * New points for an arrangement: while its control points are edited they are only kept as a draft that Apply writes
     * and Close drops; otherwise they go to [onArrangementEdit].
     */
    private val stagedEdit: (String, List<Vector3>) -> Boolean = { id, points ->
        if (state.pathEditing == id) {
            state.pathEdit = null
            state.pathDraft = PathEdit(id, points.toList())
            stateChanged()
            true
        } else {
            onArrangementEdit?.invoke(id, points) ?: false
        }
    }

    /** Whether the edit of the control points holds changes not written yet. */
    val hasPathDraft: Boolean get() = state.pathDraft != null

    /** Writes the draft points of the arrangement being edited through [onArrangementEdit]; false when there was nothing to write or it failed. */
    fun applyPathDraft(): Boolean {
        val draft = state.pathDraft ?: return false
        val written = onArrangementEdit?.invoke(draft.entityId, draft.points) ?: false
        if (written) state.pathDraft = null
        stateChanged()
        return written
    }

    /** Drops the point [index] of the arrangement being edited onto the ground, into the draft. */
    fun dropPathPoint(index: Int): Boolean = dropPath { it == index }

    /** Drops every control point of the arrangement being edited onto the ground, into the draft. */
    fun dropPath(): Boolean = dropPath { true }

    private fun dropPath(which: (Int) -> Boolean): Boolean {
        val id = state.pathEditing ?: return false
        val ground = groundHeights?.invoke() ?: return false
        val points = queries.pathPoints(id) ?: return false
        val dropped =
            points.mapIndexed { i, p -> if (which(i)) Vector3(p.x, ground.heightAt(p.x, p.z, p.y), p.z) else p }
        if (dropped == points) return false
        return stagedEdit(id, dropped)
    }

    /** The arrangement whose control points are being edited, or null. */
    val pathEditing: String? get() = state.pathEditing

    /** Starts (or, with null, ends) editing the control points of the arrangement [id], selecting it. */
    fun setPathEditing(id: String?) {
        state.pathEdit = null
        state.pathDraft = null
        state.selectedPathPoint = null
        state.pathEditing = id
        if (id != null) {
            state.selectedId = id
            state.paint = null
        }
        stateChanged()
    }

    /** Ends the control point edit when the selection is no longer its arrangement. Call after the selection changed. */
    fun selectionChanged() {
        val editing = state.pathEditing ?: return
        if (state.selectedId == editing) return
        state.pathEditing = null
        state.selectedPathPoint = null
        state.pathEdit = null
        state.pathDraft = null
        onPathEditEnded?.invoke()
    }

    private fun stateChanged() {
        selectionChanged()
        lastCanDrop = canDrop
        onStateChanged?.invoke()
    }

    /** Called outside GL after a frame; loading or params changes need one query, unchanged frames need none. */
    fun frameRendered(drawnVersion: Long = queries.drawnVersion) {
        if (drawnVersion == lastDrawnVersion && !refreshDropAfterFrame) return
        refreshDropAfterFrame = false
        lastDrawnVersion = drawnVersion
        val available = canDrop
        if (available != lastCanDrop) {
            lastCanDrop = available
            onStateChanged?.invoke()
        }
    }

    fun pressed(x: Int, y: Int, left: Boolean) {
        lastX = x
        lastY = y
        gestures.reset()
        click.pressed(x, y)
        val id = selectedId
        // Drawing mode: a press only starts a click that adds a point (or a camera drag).
        if (!left || state.drawing != null || size.isEmpty) return
        gestures.press(id, x, y)
    }

    fun dragged(x: Int, y: Int, left: Boolean) {
        click.dragged(x, y)
        val dx = (x - lastX).toFloat()
        val dy = (y - lastY).toFloat()
        lastX = x
        lastY = y
        if (size.isEmpty || gestures.drag(x, y)) return
        if (state.viewCamera != null) return
        // The left button paints in Paint Foliage mode; only the other buttons and the wheel navigate.
        if (left) {
            if (state.paint == null) orbit.orbit(dx, dy)
        } else orbit.pan(dx, dy)
    }

    fun released(x: Int, y: Int, left: Boolean, clickCount: Int = 1) {
        val wasClick = click.released(x, y)
        if (gestures.release()) return
        if (left && wasClick) {
            if (state.drawing != null) drawingSession.click(x, y, clickCount) else pickAt(x, y)
        }
    }

    /** Starts drawing a path arrangement of the model [asset]: each click on the terrain adds a point. */
    fun startDrawing(asset: RenderAsset) = drawingSession.start(asset)

    /** Enter: finishes the drawing as an open line; nothing happens, and drawing continues, with fewer than two points. */
    fun enter() = drawingSession.enter()

    /** Copy: hands the selected entity to [onCopy]. */
    fun copy() {
        val id = selectedId ?: return
        if (state.drawing == null && id != state.viewCamera) {
            onCopy?.invoke(id)
        }
    }

    /** Paste: adds the copied entity where the ray through the cursor meets the terrain, else the ground plane. */
    fun paste() {
        if (size.isEmpty || state.drawing != null || isDragging) {
            return
        }
        val ray = queries.rayFrom(size, lastX, lastY) ?: return
        val point = queries.groundHit(ray) ?: planeHit(ray) ?: return
        val written = onPaste?.invoke(point) ?: false
        if (written) {
            stateChanged()
        }
    }

    /** Where [ray] meets the plane `y = 0`, or null when it runs parallel to it or away from it. */
    private fun planeHit(ray: Ray): Vector3? {
        if (abs(ray.direction.y) < 1e-6f) {
            return null
        }
        val t = -ray.origin.y / ray.direction.y
        return if (t > 0f) {
            Vector3(ray.origin).mulAdd(ray.direction, t)
        } else null
    }

    /**
     * Backspace / Delete: drops the last point of the drawing, removes the selected control point of the arrangement
     * being edited, or otherwise removes the selected entity through [onDelete].
     */
    fun backspace() {
        val id = state.pathEditing
        val index = state.selectedPathPoint
        if (state.drawing == null && id != null && index != null) {
            if (drawingSession.removePoint(id, index)) {
                state.selectedPathPoint = null
                stateChanged()
            }
            return
        }
        if (state.drawing == null) {
            val id = selectedId
            if (id != null && id != state.viewCamera && state.pathEditing == null && !isDragging) onDelete?.invoke(id)
            return
        }
        drawingSession.backspace()
    }

    /** Whether point [index] of the arrangement [entityId] can be removed: at least two points must remain. */
    fun canRemovePoint(entityId: String): Boolean = drawingSession.canRemovePoint(entityId)

    /** Removes point [index] of the arrangement [entityId] through [onArrangementEdit]; false when it cannot be removed or was not written. */
    fun removePoint(entityId: String, index: Int): Boolean = drawingSession.removePoint(entityId, index)

    private fun editOf(id: String, result: DragResult): TransformEdit {
        if (state.gizmoMode == GizmoMode.MOVE) return TransformEdit(position = result.transform.position)
        val content = queries.sceneContent
        val handleId = content.lights.firstOrNull { it.entityId == id }?.let(content::aimHandleOf)
        // A rotate drag on a light aimed at a direction handle moves the handle instead of the light's own rotation.
        val handleAt = handleId?.let { ScenePreview().aimedTarget(content, id, result) }
        if (handleId != null && handleAt != null) return TransformEdit(target = TargetMove(handleId, handleAt))
        return TransformEdit(
            rotation = result.transform.rotation,
            direction = result.direction.takeIf { ScenePreview().isCamera(content, id) })
    }

    /** The cursor moved without a button: brightens the handle under it. */
    fun moved(x: Int, y: Int) {
        lastX = x
        lastY = y
        if (size.isEmpty || isDragging) return
        val at = size.toFramebuffer(x, y)
        state.hoveredAxis = queries.gizmoHit(at.x, at.y, size.framebufferWidth, size.framebufferHeight)
        state.hoveredPlane = if (state.hoveredAxis == null) queries.gizmoPlaneHit(
            at.x,
            at.y,
            size.framebufferWidth,
            size.framebufferHeight
        ) else null
    }

    fun wheel(clicks: Float) {
        if (state.viewCamera == null) orbit.zoom(clicks)
    }

    /** Esc: puts the dragged object back, or discards the paint stroke; neither writes anything. */
    fun escape() {
        if (state.drawing != null && gestures.isIdle) {
            drawingSession.cancel()
            return
        }
        gestures.escape()
    }

    private fun pickAt(x: Int, y: Int) {
        if (size.isEmpty) return
        if (state.pathEditing != null) {
            // editing control points: a click off the handles only lets go of the point; Apply ends the mode
            if (state.selectedPathPoint != null) {
                state.selectedPathPoint = null
                stateChanged()
            }
            return
        }
        val at = size.toFramebuffer(x, y)
        val target = queries.pickTarget(at.x, at.y, size.framebufferWidth, size.framebufferHeight)
        if (target is SceneTarget.Water) {
            val changed = state.selectedWater != target.id || state.paint != null
            state.selectedWater = target.id
            state.paint = null
            onPickWater?.invoke(target.id)
            if (changed) stateChanged()
            return
        }
        val picked = (target as? SceneTarget.Entity)?.id
        // a model that belongs to a path arrangement stands for the whole arrangement
        val hit = picked?.let { queries.content.arrangementOf(it) ?: it }
        var changed = hit != selectedId
        selectedId = hit
        // Selecting another entity (or nothing) ends the Paint Foliage mode.
        state.paint?.let { mode ->
            if (hit != mode.entityId) {
                state.paint = null
                changed = true
            }
        }
        if (hit != null) onPick?.invoke(hit)
        if (changed) stateChanged()
    }

    /** New scene params arrived: drops the preview and a selection or camera the scene no longer has. */
    fun paramsChanged(params: SceneRenderParams) {
        refreshDropAfterFrame = true
        state.preview = emptyMap()
        state.waterPreview = emptyMap()
        if (!gestures.isDraggingPathPoint) state.pathEdit = null
        var changed = false
        selectedId?.let {
            if (!ScenePreview().contains(params.content, it)) {
                selectedId = null; changed = true
            }
        }
        // a surface deleted (or made invalid) elsewhere leaves nothing selected rather than another target
        state.selectedWater?.let { id ->
            if (params.content.water.none { it.id == id }) {
                state.selection = null; changed = true
            }
        }
        state.viewCamera?.let { id ->
            if (params.content.cameras.none { it.entityId == id }) {
                state.viewCamera = null; changed = true
            }
        }
        gestures.contentChanged(params.content) { ScenePreview().contains(params.content, it) }
        state.pathEditing?.let { id ->
            if (params.content.arrangements.none { it.entityId == id }) {
                state.selection = null; changed = true
            }
        }
        state.paint?.let { mode ->
            if (!ScenePreview().contains(params.content, mode.entityId)) {
                state.paint = null
                changed = true
                if (gestures.isPainting) {
                    gestures.cancel()
                    foliagePaint?.cancelled()
                }
            }
        }
        if (changed || canDrop != lastCanDrop) stateChanged()
    }
}
