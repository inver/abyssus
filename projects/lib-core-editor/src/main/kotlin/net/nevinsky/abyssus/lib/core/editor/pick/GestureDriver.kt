/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent

/** The Swing-pixel ray through ([x], [y]) in the view [size], or null while the view is empty or the camera has no ray. */
internal fun SceneQueries.rayFrom(size: ViewSize, x: Int, y: Int): Ray? {
    val at = size.toFramebuffer(x, y)
    return rayAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight)
}

/** What [GestureDriver] reads and calls on the view's interaction; the callbacks are read on use. */
internal interface GestureHost {
    val size: ViewSize

    /** The stroke of the brush [kind], or null when nothing implements it. */
    fun brushStroke(kind: String): BrushStroke?

    val onTransform: ((String, TransformEdit) -> Boolean)?
    val onWaterMove: ((String, Vector3) -> Boolean)?
    val onArrangementEdit: ((String, List<Vector3>) -> Boolean)?

    /** The selected entity, or null. */
    val selectedId: String?

    /** The transform write for the end of a gizmo drag of [entityId]. */
    fun editOf(entityId: String, result: DragResult): TransformEdit
    fun stateChanged()
}

/** Where the current press-drag-release stands. */
internal sealed interface Gesture {
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

    /** A move handle of control point [index] of the arrangement [entityId] is dragged; [original] are its points before, [points] now. */
    class DraggingPathPoint(val drag: GizmoDrag, val entityId: String, val index: Int, val original: List<Vector3>) : Gesture {
        var points: List<Vector3> = original
        var moved = false
    }

    /** A terrain brush stroke of [kind] on the terrain of [entityId]; the caller's [BrushStroke] of that kind does the work. */
    class Brushing(val entityId: String, val kind: String) : Gesture
}

/**
 * The press-drag-release state of the scene view: starting a gizmo, water, path point or paint-stroke gesture, updating
 * it while the mouse moves, and writing or discarding its result on release or Esc. Orbiting and clicking stay with
 * [SceneInteraction], which falls back to them while the gesture is [Gesture.Idle].
 */
internal class GestureDriver(
    private val state: SceneViewState,
    private val queries: SceneQueries,
    private val host: GestureHost,
) {
    var current: Gesture = Gesture.Idle
        private set

    val isIdle: Boolean get() = current == Gesture.Idle
    val isDragging: Boolean get() = current is Gesture.Dragging || current is Gesture.DraggingWater
    val isBrushing: Boolean get() = current is Gesture.Brushing
    val isDraggingPathPoint: Boolean get() = current is Gesture.DraggingPathPoint

    /** Ends whatever gesture is under way without writing; the rest of the press does nothing. */
    fun cancel() {
        current = Gesture.Cancelled
    }

    /** Resets for a new press; a left press may then start a gesture through [press]. */
    fun reset() {
        current = Gesture.Idle
    }

    /** The left press at ([x], [y]) with the selection [id]: starts the gesture that fits what is under the cursor, if any. */
    fun press(id: String?, x: Int, y: Int) {
        val size = host.size
        state.selectedWater?.let { startWaterDrag(it, x, y); return }
        if (id == null) return
        val at = size.toFramebuffer(x, y)
        if (state.pathEditing == id) {
            // while the control points are edited the selection is the line: only its handles and the point's gizmo react
            if (!startPathGizmoDrag(id, at)) pressPathHandle(id, at)
            return
        }
        val brush = state.brush
        if (brush != null) {
            startBrush(brush, at)
            return
        }
        val started = grab(at) ?: return
        current = Gesture.Dragging(started, id)
        host.stateChanged()
    }

    /** The drag of the handle under [at], an axis arrow or ring first, else a plane square; marks it as hovered. */
    private fun grab(at: FramebufferPoint): GizmoDrag? {
        val size = host.size
        val axis = queries.gizmoHit(at.x, at.y, size.framebufferWidth, size.framebufferHeight)
        if (axis != null) {
            val started = queries.beginDrag(axis, at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return null
            state.hoveredAxis = axis
            return started
        }
        val plane = queries.gizmoPlaneHit(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return null
        val started = queries.beginPlaneDrag(plane, at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return null
        state.hoveredPlane = plane
        return started
    }

    /** The left press on a move handle of the selected water surface starts a drag of its centre. */
    private fun startWaterDrag(waterId: String, x: Int, y: Int) {
        val size = host.size
        val at = size.toFramebuffer(x, y)
        val started = grab(at) ?: return
        current = Gesture.DraggingWater(started, waterId)
        host.stateChanged()
    }

    /** The left press on a move handle of the gizmo of the selected control point of [id]. */
    private fun startPathGizmoDrag(id: String, at: FramebufferPoint): Boolean {
        val index = state.selectedPathPoint ?: return false
        val size = host.size
        val points = queries.pathPoints(id) ?: return false
        val started = grab(at) ?: return false
        current = Gesture.DraggingPathPoint(started, id, index, points)
        host.stateChanged()
        return true
    }

    /**
     * The left press on a handle of the line of [id]: a control point gets the move gizmo, and the curve between points
     * gets a new control point where it was pressed, which gets the gizmo. The press is consumed, so it neither orbits
     * nor picks.
     */
    private fun pressPathHandle(id: String, at: FramebufferPoint): Boolean {
        val size = host.size
        val hit = queries.pathHandleAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return false
        when (hit) {
            is PathHit.Point -> state.selectedPathPoint = hit.index
            is PathHit.Line -> {
                val points = queries.pathPoints(id) ?: return false
                val inserted = points.toMutableList().also { it.add(hit.span + 1, hit.at) }
                if (host.onArrangementEdit?.invoke(id, inserted) == true) state.selectedPathPoint = hit.span + 1
            }
        }
        current = Gesture.Cancelled
        host.stateChanged()
        return true
    }

    /** The left press of a brush mode: a stroke starts only where the ray hits the brushed terrain. */
    private fun startBrush(brush: BrushMode, at: FramebufferPoint) {
        val size = host.size
        if (brush.entityId != host.selectedId) return
        val ray = queries.rayAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
        val terrain = queries.terrainOf(brush.entityId) ?: return
        val hit = queries.terrainHit(brush.entityId, ray) ?: return
        if (host.brushStroke(brush.kind)?.pressed(terrain, hit) != true) return
        current = Gesture.Brushing(brush.entityId, brush.kind)
        host.stateChanged()
    }

    /**
     * The mouse moved to ([x], [y]) with a button down. True when a gesture took the move (or cancelled it); false in
     * [Gesture.Idle], where the caller orbits or pans.
     */
    fun drag(x: Int, y: Int): Boolean {
        val size = host.size
        when (val g = current) {
            Gesture.Idle -> return false
            Gesture.Cancelled -> Unit
            is Gesture.Dragging -> {
                val ray = queries.rayFrom(size, x, y) ?: return true
                val result = g.drag.update(ray)
                g.result = result
                state.preview = mapOf(g.entityId to result)
            }
            is Gesture.DraggingWater -> {
                val ray = queries.rayFrom(size, x, y) ?: return true
                val result = g.drag.update(ray)
                g.result = result
                state.waterPreview = mapOf(g.waterId to result.transform.position)
            }
            is Gesture.DraggingPathPoint -> {
                val ray = queries.rayFrom(size, x, y) ?: return true
                val to = g.drag.update(ray).transform.position
                val moved = g.original.toMutableList().also { it[g.index] = to }
                g.points = moved
                g.moved = true
                state.pathEdit = PathEdit(g.entityId, moved)
            }
            is Gesture.Brushing -> {
                val mode = state.brush?.takeIf { it.entityId == g.entityId && it.kind == g.kind } ?: return true
                val ray = queries.rayFrom(size, x, y) ?: return true
                val hit = queries.terrainHit(g.entityId, ray) ?: return true
                host.brushStroke(g.kind)?.dragged(hit, mode.reversed)
            }
        }
        return true
    }

    /** The button was released: writes or discards the gesture's result and returns to [Gesture.Idle]. True unless it already was idle. */
    fun release(): Boolean {
        val ended = current
        current = Gesture.Idle
        state.hoveredAxis = null
        state.hoveredPlane = null
        when (ended) {
            Gesture.Idle -> return false
            Gesture.Cancelled -> Unit
            is Gesture.DraggingPathPoint -> {
                if (ended.moved && ended.points != ended.original) {
                    val written = host.onArrangementEdit?.invoke(ended.entityId, ended.points) ?: false
                    if (!written) state.pathEdit = null
                } else {
                    state.pathEdit = null
                }
            }
            is Gesture.Brushing -> host.brushStroke(ended.kind)?.released()
            is Gesture.DraggingWater -> {
                val result = ended.result
                if (result != null && result.transform != ended.drag.start) {
                    val written = host.onWaterMove?.invoke(ended.waterId, result.transform.position) ?: false
                    if (!written) state.waterPreview = emptyMap()
                } else {
                    state.waterPreview = emptyMap()
                }
            }
            is Gesture.Dragging -> {
                val result = ended.result
                if (result != null && result.transform != ended.drag.start) {
                    val written = host.onTransform?.invoke(ended.entityId, host.editOf(ended.entityId, result)) ?: false
                    if (!written) state.preview = emptyMap()
                } else {
                    state.preview = emptyMap()
                }
            }
        }
        host.stateChanged()
        return true
    }

    /** Esc: puts the dragged object back, or discards the brush stroke; neither writes anything. True when a gesture was cancelled. */
    fun escape(): Boolean {
        when (current) {
            is Gesture.DraggingPathPoint -> {
                current = Gesture.Cancelled
                state.pathEdit = null
            }
            is Gesture.Brushing -> {
                val kind = (current as Gesture.Brushing).kind
                current = Gesture.Cancelled
                host.brushStroke(kind)?.cancelled()
            }
            is Gesture.Dragging, is Gesture.DraggingWater -> {
                current = Gesture.Cancelled
                state.preview = emptyMap()
                state.waterPreview = emptyMap()
                state.hoveredAxis = null
                state.hoveredPlane = null
            }
            else -> return false
        }
        host.stateChanged()
        return true
    }

    /** New scene content arrived: cancels a drag whose water surface, entity or arrangement the scene no longer has. */
    fun contentChanged(content: SceneContent, contains: (String) -> Boolean) {
        (current as? Gesture.DraggingWater)?.let { if (content.water.none { w -> w.id == it.waterId }) current = Gesture.Cancelled }
        (current as? Gesture.Dragging)?.let { if (!contains(it.entityId)) current = Gesture.Cancelled }
        (current as? Gesture.DraggingPathPoint)?.let {
            if (!contains(it.entityId)) {
                current = Gesture.Cancelled
                state.pathEdit = null
            }
        }
    }
}
