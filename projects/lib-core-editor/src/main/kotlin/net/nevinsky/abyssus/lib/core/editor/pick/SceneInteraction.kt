/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset


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
    var onWaterMove: ((String, Vec3) -> Boolean)? = null

    /** Called when a drag ends with a changed transform; returns whether it was written to the scene. */
    var onTransform: ((String, TransformEdit) -> Boolean)? = null

    /**
     * Called when a drawing is finished with the model, the points on the terrain and whether the line is closed;
     * returns whether the arrangement was written. A refused write keeps the drawing.
     */
    var onArrangementCreate: ((RenderAsset, List<Vec3>, Boolean) -> Boolean)? = null

    /** Called when a point drag, an insert or a removal ends with new points for the arrangement; returns whether it was written. */
    var onArrangementEdit: ((String, List<Vec3>) -> Boolean)? = null

    /** Called when the selection, the mode or the looked-through camera changed by the user or by new scene params. */
    var onStateChanged: (() -> Unit)? = null

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

    /** Where the current press-drag-release stands. */
    private sealed interface Gesture {
        /** Nothing special: a drag orbits or pans, a release without a drag is a click. */
        data object Idle : Gesture

        /** A gizmo handle of [entityId] is being dragged; [result] is where it is now. */
        class Dragging(val drag: GizmoDrag, val entityId: String) : Gesture {
            var result: DragResult? = null
        }

        /** A move handle of the water surface [waterId] is being dragged; [result] is where its centre is now. */
        class DraggingWater(val drag: GizmoDrag, val waterId: String) : Gesture {
            var result: DragResult? = null
        }

        /** Set by Esc (or by the dragged entity leaving the scene): the rest of the gesture, up to the release, does nothing. */
        data object Cancelled : Gesture

        /** Point [index] of the arrangement [entityId] is being dragged; [original] are its points before, [points] now. */
        class DraggingPathPoint(val entityId: String, val index: Int, val original: List<Vec3>) : Gesture {
            var points: List<Vec3> = original
            var moved = false
        }

        /** The Paint Foliage stroke on the terrain of [entityId]; the caller's [FoliagePaint] does the painting. */
        class Painting(val entityId: String) : Gesture
    }

    private val click = ClickGesture()
    private var lastX = 0
    private var lastY = 0
    private var gesture: Gesture = Gesture.Idle

    val isDragging: Boolean get() = gesture is Gesture.Dragging || gesture is Gesture.DraggingWater

    /** The arrangement being drawn, or null outside the drawing mode. */
    val drawing: PathDrawing? get() = state.drawing

    private var lastDrawnVersion = -1L
    private var lastCanDrop = false
    private var refreshDropAfterFrame = false

    val canDrop: Boolean get() = dropPosition() != null

    private fun dropPosition(): Vec3? {
        val id = selectedId ?: return null
        val content = queries.content
        if (isDragging || id == state.viewCamera || content.terrains.any { it.entityId == id }) return null
        val selected = ScenePreview().selected(content, id) ?: return null
        val lowest = queries.lowestPoint(id) ?: return null
        val height = queries.groundBelow(id) ?: return null
        if (!height.isFinite() || ScenePicker().isResting(lowest, height)) return null
        val p = selected.transform.position
        return Vec3(p.x, (p.y.toDouble() + height.toDouble() - lowest.toDouble()).toFloat(), p.z)
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

    private fun stateChanged() {
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
        gesture = Gesture.Idle
        click.pressed(x, y)
        val id = selectedId
        // Drawing mode: a press only starts a click that adds a point (or a camera drag).
        if (!left || state.drawing != null || size.isEmpty) return
        state.selectedWater?.let { startWaterDrag(it, x, y); return }
        if (id == null) return
        val at = size.toFramebuffer(x, y)
        if (startPathDrag(id, at)) return
        val paint = state.paint
        if (paint != null) {
            startStroke(paint, at)
            return
        }
        val axis = queries.gizmoHit(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
        val started = queries.beginDrag(axis, at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
        gesture = Gesture.Dragging(started, id)
        state.hoveredAxis = axis
        stateChanged()
    }

    /** The left press on a move handle of the selected water surface starts a drag of its centre. */
    private fun startWaterDrag(waterId: String, x: Int, y: Int) {
        val at = size.toFramebuffer(x, y)
        val axis = queries.gizmoHit(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
        val started = queries.beginDrag(axis, at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
        gesture = Gesture.DraggingWater(started, waterId)
        state.hoveredAxis = axis
        stateChanged()
    }

    /** The left press on a handle of the selected arrangement's line: a point drag, or an insert from a middle handle. */
    private fun startPathDrag(id: String, at: FramebufferPoint): Boolean {
        val points = queries.pathPoints(id) ?: return false
        val hit = queries.pathHandleAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return false
        when (hit) {
            is PathHit.Point -> gesture = Gesture.DraggingPathPoint(id, hit.index, points)
            is PathHit.Middle -> {
                val closed = queries.content.arrangements.firstOrNull { it.entityId == id }?.arrangement?.closed == true
                val middle = PathHandles(points, closed).middle(hit.segment)
                val inserted = points.toMutableList().also { it.add(hit.segment + 1, middle) }
                gesture = Gesture.DraggingPathPoint(id, hit.segment + 1, points).also { it.points = inserted }
            }
        }
        stateChanged()
        return true
    }

    /** The left press of Paint Foliage mode: a stroke starts only where the ray hits the painted terrain. */
    private fun startStroke(paint: FoliagePaintMode, at: FramebufferPoint) {
        if (paint.entityId != selectedId) return
        val ray = queries.rayAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
        val terrain = queries.terrainOf(paint.entityId) ?: return
        val hit = queries.terrainHit(paint.entityId, ray) ?: return
        if (foliagePaint?.pressed(terrain, hit) != true) return
        gesture = Gesture.Painting(paint.entityId)
        stateChanged()
    }

    fun dragged(x: Int, y: Int, left: Boolean) {
        click.dragged(x, y)
        val dx = (x - lastX).toFloat()
        val dy = (y - lastY).toFloat()
        lastX = x
        lastY = y
        if (size.isEmpty) return
        when (val g = gesture) {
            Gesture.Cancelled -> return
            is Gesture.Dragging -> {
                val at = size.toFramebuffer(x, y)
                val ray = queries.rayAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
                val result = g.drag.update(ray)
                g.result = result
                state.preview = mapOf(g.entityId to result)
            }
            is Gesture.DraggingWater -> {
                val at = size.toFramebuffer(x, y)
                val ray = queries.rayAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
                val result = g.drag.update(ray)
                g.result = result
                state.waterPreview = mapOf(g.waterId to result.transform.position)
            }
            is Gesture.DraggingPathPoint -> {
                val at = size.toFramebuffer(x, y)
                val ray = queries.rayAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
                val hit = queries.groundHit(ray) ?: return
                val moved = g.points.toMutableList().also { it[g.index] = Vec3(hit.x, hit.y, hit.z) }
                g.points = moved
                g.moved = true
                state.pathEdit = PathEdit(g.entityId, moved)
            }
            is Gesture.Painting -> {
                val mode = state.paint?.takeIf { it.entityId == g.entityId } ?: return
                val at = size.toFramebuffer(x, y)
                val ray = queries.rayAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
                val hit = queries.terrainHit(g.entityId, ray) ?: return
                foliagePaint?.dragged(hit, mode.erase)
            }
            Gesture.Idle -> {
                if (state.viewCamera != null) return
                // The left button paints in Paint Foliage mode; only the other buttons and the wheel navigate.
                if (left) {
                    if (state.paint == null) orbit.orbit(dx, dy)
                } else orbit.pan(dx, dy)
            }
        }
    }

    fun released(x: Int, y: Int, left: Boolean, clickCount: Int = 1) {
        val wasClick = click.released(x, y)
        val ended = gesture
        gesture = Gesture.Idle
        state.hoveredAxis = null
        when (ended) {
            Gesture.Cancelled -> stateChanged()
            is Gesture.DraggingPathPoint -> {
                if (ended.moved && ended.points != ended.original) {
                    val written = onArrangementEdit?.invoke(ended.entityId, ended.points) ?: false
                    if (!written) state.pathEdit = null
                } else {
                    state.pathEdit = null
                }
                stateChanged()
            }
            is Gesture.Painting -> {
                foliagePaint?.released()
                stateChanged()
            }
            is Gesture.DraggingWater -> {
                val result = ended.result
                if (result != null && result.transform != ended.drag.start) {
                    val written = onWaterMove?.invoke(ended.waterId, result.transform.position) ?: false
                    if (!written) state.waterPreview = emptyMap()
                } else {
                    state.waterPreview = emptyMap()
                }
                stateChanged()
            }
            is Gesture.Dragging -> {
                val result = ended.result
                if (result != null && result.transform != ended.drag.start) {
                    val written = onTransform?.invoke(ended.entityId, editOf(ended.entityId, result)) ?: false
                    if (!written) state.preview = emptyMap()
                } else {
                    state.preview = emptyMap()
                }
                stateChanged()
            }
            Gesture.Idle -> if (left && wasClick) {
                if (state.drawing != null) drawingClick(x, y, clickCount) else pickAt(x, y)
            }
        }
    }

    /** Starts drawing a path arrangement of the model [asset]: each click on the terrain adds a point. */
    fun startDrawing(asset: RenderAsset) {
        state.drawing = PathDrawing(asset)
        state.paint = null
        stateChanged()
    }

    /** A click in drawing mode: closes the loop on the first point, finishes on a double click, else adds a point where it hits the terrain. */
    private fun drawingClick(x: Int, y: Int, clickCount: Int) {
        val drawing = state.drawing ?: return
        if (size.isEmpty) return
        // the first click of a double click already added its point
        if (clickCount >= 2) {
            finishDrawing(closed = false)
            return
        }
        val at = size.toFramebuffer(x, y)
        if (drawing.points.size >= 3) {
            val distance = queries.screenDistance(drawing.points.first(), at.x, at.y, size.framebufferWidth, size.framebufferHeight)
            if (distance != null && distance <= PATH_HANDLE_PX) {
                finishDrawing(closed = true)
                return
            }
        }
        val ray = queries.rayAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
        val hit = queries.groundHit(ray) ?: return
        drawing.points += Vec3(hit.x, hit.y, hit.z)
        stateChanged()
    }

    /** Enter: finishes the drawing as an open line; nothing happens, and drawing continues, with fewer than two points. */
    fun enter() = finishDrawing(closed = false)

    private fun finishDrawing(closed: Boolean) {
        val drawing = state.drawing ?: return
        if (drawing.points.size < 2) return
        val written = onArrangementCreate?.invoke(drawing.asset, drawing.points.toList(), closed) ?: false
        if (written) {
            state.drawing = null
            stateChanged()
        }
    }

    /** Backspace: drops the last point of the drawing. */
    fun backspace() {
        val drawing = state.drawing ?: return
        if (drawing.points.isEmpty()) return
        drawing.points.removeAt(drawing.points.lastIndex)
        stateChanged()
    }

    /** Whether point [index] of the arrangement [entityId] can be removed: at least two points must remain. */
    fun canRemovePoint(entityId: String): Boolean = (queries.pathPoints(entityId)?.size ?: 0) > 2

    /** Removes point [index] of the arrangement [entityId] through [onArrangementEdit]; false when it cannot be removed or was not written. */
    fun removePoint(entityId: String, index: Int): Boolean {
        val points = queries.pathPoints(entityId) ?: return false
        if (points.size <= 2 || index !in points.indices) return false
        return onArrangementEdit?.invoke(entityId, points.toMutableList().also { it.removeAt(index) }) ?: false
    }

    private fun editOf(id: String, result: DragResult): TransformEdit {
        if (state.gizmoMode == GizmoMode.MOVE) return TransformEdit(position = result.transform.position)
        val content = queries.sceneContent
        val handleId = content.lights.firstOrNull { it.entityId == id }?.let(content::aimHandleOf)
        // A rotate drag on a light aimed at a direction handle moves the handle instead of the light's own rotation.
        val handleAt = handleId?.let { ScenePreview().aimedTarget(content, id, result) }
        if (handleId != null && handleAt != null) return TransformEdit(target = TargetMove(handleId, handleAt))
        return TransformEdit(rotation = result.transform.rotation, direction = result.direction.takeIf { ScenePreview().isCamera(content, id) })
    }

    /** The cursor moved without a button: brightens the handle under it. */
    fun moved(x: Int, y: Int) {
        if (size.isEmpty || isDragging) return
        val at = size.toFramebuffer(x, y)
        state.hoveredAxis = queries.gizmoHit(at.x, at.y, size.framebufferWidth, size.framebufferHeight)
    }

    fun wheel(clicks: Float) {
        if (state.viewCamera == null) orbit.zoom(clicks)
    }

    /** Esc: puts the dragged object back, or discards the paint stroke; neither writes anything. */
    fun escape() {
        if (gesture is Gesture.DraggingPathPoint) {
            gesture = Gesture.Cancelled
            state.pathEdit = null
            stateChanged()
            return
        }
        if (state.drawing != null && gesture == Gesture.Idle) {
            state.drawing = null
            stateChanged()
            return
        }
        if (gesture is Gesture.Painting) {
            gesture = Gesture.Cancelled
            foliagePaint?.cancelled()
            stateChanged()
            return
        }
        if (!isDragging) return
        gesture = Gesture.Cancelled
        state.preview = emptyMap()
        state.waterPreview = emptyMap()
        state.hoveredAxis = null
        stateChanged()
    }

    private fun pickAt(x: Int, y: Int) {
        if (size.isEmpty) return
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
        // A line lies on the terrain, so it wins over the terrain under it but not over a model (a copy) in front of it.
        val line = if (picked == null || queries.content.terrains.any { it.entityId == picked }) {
            queries.pathLineAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight)
        } else null
        val hit = line ?: picked
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
        if (gesture !is Gesture.DraggingPathPoint) state.pathEdit = null
        var changed = false
        selectedId?.let { if (!ScenePreview().contains(params.content, it)) { selectedId = null; changed = true } }
        // a surface deleted (or made invalid) elsewhere leaves nothing selected rather than another target
        state.selectedWater?.let { id ->
            if (params.content.water.none { it.id == id }) { state.selection = null; changed = true }
        }
        (gesture as? Gesture.DraggingWater)?.let { if (params.content.water.none { w -> w.id == it.waterId }) gesture = Gesture.Cancelled }
        state.viewCamera?.let { id -> if (params.content.cameras.none { it.entityId == id }) { state.viewCamera = null; changed = true } }
        val dragging = gesture as? Gesture.Dragging
        if (dragging != null && !ScenePreview().contains(params.content, dragging.entityId)) gesture = Gesture.Cancelled
        (gesture as? Gesture.DraggingPathPoint)?.let {
            if (!ScenePreview().contains(params.content, it.entityId)) {
                gesture = Gesture.Cancelled
                state.pathEdit = null
            }
        }
        state.paint?.let { mode ->
            if (!ScenePreview().contains(params.content, mode.entityId)) {
                state.paint = null
                changed = true
                if (gesture is Gesture.Painting) {
                    gesture = Gesture.Cancelled
                    foliagePaint?.cancelled()
                }
            }
        }
        if (changed || canDrop != lastCanDrop) stateChanged()
    }
}
