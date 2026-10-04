/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.sceneview.gizmo.DragResult
import net.nevinsky.abyssus.sceneview.gizmo.GizmoDrag
import net.nevinsky.abyssus.sceneview.gizmo.GizmoMode

/** The size of the view in Swing pixels and of its framebuffer, which is larger on HiDPI screens. */
class ViewSize(val width: Int, val height: Int, val framebufferWidth: Int, val framebufferHeight: Int) {
    val isEmpty: Boolean get() = width <= 0 || height <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0

    fun fx(x: Int): Int = x * framebufferWidth / width

    fun fy(y: Int): Int = y * framebufferHeight / height
}

/**
 * What the mouse and keys do in the scene view, apart from Swing and GL: selecting by click, orbiting and panning,
 * dragging gizmo handles and cancelling a drag. Mouse positions are Swing pixels of the view described by [size].
 * Everything else the view shows is held by the [renderer].
 */
class SceneInteraction(
    private val renderer: SceneRenderer,
    private val orbit: OrbitCamera,
    private val groundBelow: (String) -> Float? = renderer::groundBelow,
) {
    var size = ViewSize(0, 0, 0, 0)

    /** Called with the entity id of a click that selected something. */
    var onPick: ((String) -> Unit)? = null

    /** Called when a drag ends with a changed transform; returns whether it was written to the scene. */
    var onTransform: ((String, TransformEdit) -> Boolean)? = null

    /** Called when the selection, the mode or the looked-through camera changed by the user or by new scene params. */
    var onStateChanged: (() -> Unit)? = null

    var selectedId: String?
        get() = renderer.selectedId
        private set(value) {
            renderer.selectedId = value
        }

    var mode: GizmoMode
        get() = renderer.gizmoMode
        set(value) {
            renderer.gizmoMode = value
            stateChanged()
        }

    var viewCamera: String?
        get() = renderer.viewCamera
        set(value) {
            renderer.viewCamera = value
            stateChanged()
        }

    private val click = ClickGesture()
    private var lastX = 0
    private var lastY = 0
    private var drag: GizmoDrag? = null
    private var dragEntity: String? = null
    private var dragResult: DragResult? = null

    /** Set by Esc: the rest of the gesture, up to the release, does nothing. */
    private var cancelled = false

    val isDragging: Boolean get() = drag != null

    private var lastDrawnVersion = -1L
    private var lastCanDrop = false
    private var refreshDropAfterFrame = false

    val canDrop: Boolean get() = dropPosition() != null

    private fun dropPosition(): Vec3? {
        val id = selectedId ?: return null
        if (isDragging || id == viewCamera || renderer.content.terrains.any { it.entityId == id }) return null
        val selected = ScenePreview.selected(renderer.content, id) ?: return null
        val lowest = renderer.lowestPoint(id) ?: return null
        val height = groundBelow(id) ?: return null
        if (!height.isFinite() || ScenePicker.isResting(lowest, height)) return null
        val p = selected.transform.position
        return Vec3(p.x, (p.y.toDouble() + height.toDouble() - lowest.toDouble()).toFloat(), p.z)
    }

    /** A drop uses the move's preview and write callback, including its rejected-write behaviour. */
    fun drop() {
        val id = selectedId ?: return
        val position = dropPosition() ?: return
        val selected = ScenePreview.selected(renderer.content, id) ?: return
        val result = DragResult(selected.transform.copy(position = position), selected.direction)
        renderer.preview = mapOf(id to result)
        val written = onTransform?.invoke(id, TransformEdit(position = position)) ?: false
        // A synchronous document re-read can clear the preview during the callback. Keep the accepted result
        // until the renderer has another frame; a second key press must use the newly settled box immediately.
        renderer.preview = if (written) mapOf(id to result) else emptyMap()
        stateChanged()
    }

    private fun stateChanged() {
        lastCanDrop = canDrop
        onStateChanged?.invoke()
    }

    /** Called outside GL after a frame; loading or params changes need one query, unchanged frames need none. */
    fun frameRendered(drawnVersion: Long = renderer.drawnVersion) {
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
        cancelled = false
        click.pressed(x, y)
        val id = selectedId
        if (!left || id == null || size.isEmpty) return
        val axis = renderer.gizmoHit(size.fx(x), size.fy(y), size.framebufferWidth, size.framebufferHeight) ?: return
        val started = renderer.beginDrag(axis, size.fx(x), size.fy(y), size.framebufferWidth, size.framebufferHeight) ?: return
        drag = started
        dragEntity = id
        dragResult = null
        renderer.hoveredAxis = axis
        stateChanged()
    }

    fun dragged(x: Int, y: Int, left: Boolean) {
        click.dragged(x, y)
        val dx = (x - lastX).toFloat()
        val dy = (y - lastY).toFloat()
        lastX = x
        lastY = y
        if (cancelled || size.isEmpty) return
        val active = drag
        val id = dragEntity
        if (active != null && id != null) {
            val ray = renderer.rayAt(size.fx(x), size.fy(y), size.framebufferWidth, size.framebufferHeight) ?: return
            val result = active.update(ray)
            dragResult = result
            renderer.preview = mapOf(id to result)
            return
        }
        if (viewCamera != null) return
        if (left) orbit.orbit(dx, dy) else orbit.pan(dx, dy)
    }

    fun released(x: Int, y: Int, left: Boolean) {
        val wasClick = click.released(x, y)
        val active = drag
        val id = dragEntity
        val result = dragResult
        drag = null
        dragEntity = null
        dragResult = null
        renderer.hoveredAxis = null
        if (cancelled) {
            cancelled = false
            stateChanged()
            return
        }
        if (active != null && id != null) {
            if (result != null && result.transform != active.start) {
                val written = onTransform?.invoke(id, editOf(id, result)) ?: false
                if (!written) renderer.preview = emptyMap()
            } else {
                renderer.preview = emptyMap()
            }
            stateChanged()
            return
        }
        if (left && wasClick) pickAt(x, y)
    }

    private fun editOf(id: String, result: DragResult): TransformEdit {
        if (renderer.gizmoMode == GizmoMode.MOVE) return TransformEdit(position = result.transform.position)
        val content = renderer.params.content
        val handleId = content.lights.firstOrNull { it.entityId == id }?.let(content::aimHandleOf)
        // A rotate drag on a light aimed at a direction handle moves the handle instead of the light's own rotation.
        val handleAt = handleId?.let { ScenePreview.aimedTarget(content, id, result) }
        if (handleId != null && handleAt != null) return TransformEdit(target = TargetMove(handleId, handleAt))
        return TransformEdit(rotation = result.transform.rotation, direction = result.direction.takeIf { ScenePreview.isCamera(content, id) })
    }

    /** The cursor moved without a button: brightens the handle under it. */
    fun moved(x: Int, y: Int) {
        if (size.isEmpty || drag != null) return
        renderer.hoveredAxis = renderer.gizmoHit(size.fx(x), size.fy(y), size.framebufferWidth, size.framebufferHeight)
    }

    fun wheel(clicks: Float) {
        if (viewCamera == null) orbit.zoom(clicks)
    }

    /** Esc: puts the dragged object back and ends the drag without writing anything. */
    fun escape() {
        if (drag == null) return
        drag = null
        dragEntity = null
        dragResult = null
        cancelled = true
        renderer.preview = emptyMap()
        renderer.hoveredAxis = null
        stateChanged()
    }

    private fun pickAt(x: Int, y: Int) {
        if (size.isEmpty) return
        val hit = renderer.pick(size.fx(x), size.fy(y), size.framebufferWidth, size.framebufferHeight)
        val changed = hit != selectedId
        selectedId = hit
        if (hit != null) onPick?.invoke(hit)
        if (changed) stateChanged()
    }

    /** New scene params arrived: drops the preview and a selection or camera the scene no longer has. */
    fun paramsChanged(params: SceneRenderParams) {
        refreshDropAfterFrame = true
        renderer.preview = emptyMap()
        var changed = false
        selectedId?.let { if (!ScenePreview.contains(params.content, it)) { selectedId = null; changed = true } }
        viewCamera?.let { id -> if (params.content.cameras.none { it.entityId == id }) { renderer.viewCamera = null; changed = true } }
        if (drag != null && dragEntity?.let { ScenePreview.contains(params.content, it) } != true) {
            drag = null
            dragEntity = null
            dragResult = null
            cancelled = true
        }
        if (changed || canDrop != lastCanDrop) stateChanged()
    }
}
