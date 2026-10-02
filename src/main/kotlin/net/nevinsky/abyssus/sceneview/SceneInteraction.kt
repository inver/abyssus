/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
class SceneInteraction(private val renderer: SceneRenderer, private val orbit: OrbitCamera) {
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
            onStateChanged?.invoke()
        }

    var viewCamera: String?
        get() = renderer.viewCamera
        set(value) {
            renderer.viewCamera = value
            onStateChanged?.invoke()
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
            return
        }
        if (active != null && id != null) {
            if (result != null && result.transform != active.start) {
                val written = onTransform?.invoke(id, editOf(id, result)) ?: false
                if (!written) renderer.preview = emptyMap()
            } else {
                renderer.preview = emptyMap()
            }
            return
        }
        if (left && wasClick) pickAt(x, y)
    }

    private fun editOf(id: String, result: DragResult): TransformEdit =
        if (renderer.gizmoMode == GizmoMode.MOVE) TransformEdit(position = result.transform.position)
        else TransformEdit(rotation = result.transform.rotation, direction = result.direction.takeIf { ScenePreview.isCamera(renderer.params.content, id) })

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
    }

    private fun pickAt(x: Int, y: Int) {
        if (size.isEmpty) return
        val hit = renderer.pick(size.fx(x), size.fy(y), size.framebufferWidth, size.framebufferHeight)
        val changed = hit != selectedId
        selectedId = hit
        if (hit != null) onPick?.invoke(hit)
        if (changed) onStateChanged?.invoke()
    }

    /** New scene params arrived: drops the preview and a selection or camera the scene no longer has. */
    fun paramsChanged(params: SceneRenderParams) {
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
        if (changed) onStateChanged?.invoke()
    }
}
