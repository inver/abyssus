/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import net.nevinsky.abyssus.lib.core.editor.content.Vec3

/**
 * Drawing a path arrangement and editing its points: each click on the terrain adds a point, a click on the first point
 * closes the loop and Enter or a double click finishes an open line. The callbacks are read on use, because the view
 * assigns them after construction. [changed] reports a change of [state] that the toolbar shows.
 */
internal class DrawingSession(
    private val state: SceneViewState,
    private val queries: SceneQueries,
    private val size: () -> ViewSize,
    private val onCreate: () -> ((RenderAsset, List<Vec3>, Boolean) -> Boolean)?,
    private val onEdit: () -> ((String, List<Vec3>) -> Boolean)?,
    private val changed: () -> Unit,
) {
    /** Starts drawing a path arrangement of the model [asset]. */
    fun start(asset: RenderAsset) {
        state.drawing = PathDrawing(asset)
        state.paint = null
        changed()
    }

    /** A click in drawing mode: closes the loop on the first point, finishes on a double click, else adds a point where it hits the terrain. */
    fun click(x: Int, y: Int, clickCount: Int) {
        val drawing = state.drawing ?: return
        val view = size()
        if (view.isEmpty) return
        // the first click of a double click already added its point
        if (clickCount >= 2) {
            finish(closed = false)
            return
        }
        val at = view.toFramebuffer(x, y)
        if (drawing.points.size >= 3) {
            val distance = queries.screenDistance(drawing.points.first(), at.x, at.y, view.framebufferWidth, view.framebufferHeight)
            if (distance != null && distance <= PATH_HANDLE_PX) {
                finish(closed = true)
                return
            }
        }
        val ray = queries.rayFrom(view, x, y) ?: return
        val hit = queries.groundHit(ray) ?: return
        drawing.points += Vec3(hit.x, hit.y, hit.z)
        changed()
    }

    /** Enter: finishes the drawing as an open line; nothing happens, and drawing continues, with fewer than two points. */
    fun enter() = finish(closed = false)

    private fun finish(closed: Boolean) {
        val drawing = state.drawing ?: return
        if (drawing.points.size < 2) return
        val written = onCreate()?.invoke(drawing.asset, drawing.points.toList(), closed) ?: false
        if (written) {
            state.drawing = null
            changed()
        }
    }

    /** Backspace: drops the last point of the drawing. */
    fun backspace() {
        val drawing = state.drawing ?: return
        if (drawing.points.isEmpty()) return
        drawing.points.removeAt(drawing.points.lastIndex)
        changed()
    }

    /** Esc: abandons the drawing. */
    fun cancel() {
        state.drawing = null
        changed()
    }

    /** Whether a point of the arrangement [entityId] can be removed: at least two points must remain. */
    fun canRemovePoint(entityId: String): Boolean = (queries.pathPoints(entityId)?.size ?: 0) > 2

    /** Removes point [index] of the arrangement [entityId] through the edit callback; false when it cannot be removed or was not written. */
    fun removePoint(entityId: String, index: Int): Boolean {
        val points = queries.pathPoints(entityId) ?: return false
        if (points.size <= 2 || index !in points.indices) return false
        return onEdit()?.invoke(entityId, points.toMutableList().also { it.removeAt(index) }) ?: false
    }
}
