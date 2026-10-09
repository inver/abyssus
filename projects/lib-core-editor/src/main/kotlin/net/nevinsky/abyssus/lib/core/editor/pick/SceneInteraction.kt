/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.pick

import net.nevinsky.abyssus.lib.gdx.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3


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

    /** Called when a drag ends with a changed transform; returns whether it was written to the scene. */
    var onTransform: ((String, TransformEdit) -> Boolean)? = null

    /** Called when the selection, the mode or the looked-through camera changed by the user or by new scene params. */
    var onStateChanged: (() -> Unit)? = null

    var selectedId: String?
        get() = state.selectedId
        private set(value) {
            state.selectedId = value
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

        /** Set by Esc (or by the dragged entity leaving the scene): the rest of the gesture, up to the release, does nothing. */
        data object Cancelled : Gesture
    }

    private val click = ClickGesture()
    private var lastX = 0
    private var lastY = 0
    private var gesture: Gesture = Gesture.Idle

    val isDragging: Boolean get() = gesture is Gesture.Dragging

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
        if (!left || id == null || size.isEmpty) return
        val at = size.toFramebuffer(x, y)
        val axis = queries.gizmoHit(at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
        val started = queries.beginDrag(axis, at.x, at.y, size.framebufferWidth, size.framebufferHeight) ?: return
        gesture = Gesture.Dragging(started, id)
        state.hoveredAxis = axis
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
            Gesture.Idle -> {
                if (state.viewCamera != null) return
                if (left) orbit.orbit(dx, dy) else orbit.pan(dx, dy)
            }
        }
    }

    fun released(x: Int, y: Int, left: Boolean) {
        val wasClick = click.released(x, y)
        val ended = gesture
        gesture = Gesture.Idle
        state.hoveredAxis = null
        when (ended) {
            Gesture.Cancelled -> stateChanged()
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
            Gesture.Idle -> if (left && wasClick) pickAt(x, y)
        }
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

    /** Esc: puts the dragged object back and ends the drag without writing anything. */
    fun escape() {
        if (!isDragging) return
        gesture = Gesture.Cancelled
        state.preview = emptyMap()
        state.hoveredAxis = null
        stateChanged()
    }

    private fun pickAt(x: Int, y: Int) {
        if (size.isEmpty) return
        val at = size.toFramebuffer(x, y)
        val hit = queries.pick(at.x, at.y, size.framebufferWidth, size.framebufferHeight)
        val changed = hit != selectedId
        selectedId = hit
        if (hit != null) onPick?.invoke(hit)
        if (changed) stateChanged()
    }

    /** New scene params arrived: drops the preview and a selection or camera the scene no longer has. */
    fun paramsChanged(params: SceneRenderParams) {
        refreshDropAfterFrame = true
        state.preview = emptyMap()
        var changed = false
        selectedId?.let { if (!ScenePreview().contains(params.content, it)) { selectedId = null; changed = true } }
        state.viewCamera?.let { id -> if (params.content.cameras.none { it.entityId == id }) { state.viewCamera = null; changed = true } }
        val dragging = gesture as? Gesture.Dragging
        if (dragging != null && !ScenePreview().contains(params.content, dragging.entityId)) gesture = Gesture.Cancelled
        if (changed || canDrop != lastCanDrop) stateChanged()
    }
}
