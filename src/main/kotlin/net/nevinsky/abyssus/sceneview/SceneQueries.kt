/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.sceneview.gizmo.GizmoAxis
import net.nevinsky.abyssus.sceneview.gizmo.GizmoDrag
import net.nevinsky.abyssus.sceneview.gizmo.GizmoHandles
import net.nevinsky.abyssus.sceneview.gizmo.GizmoHit
import net.nevinsky.abyssus.sceneview.gizmo.GizmoMode
import net.nevinsky.abyssus.sceneview.gizmo.canRotate
import net.nevinsky.abyssus.sceneview.gizmo.DragResult

/**
 * The questions the view asks about the scene as the last frame drew it (what is under the cursor, where an object would
 * rest, which gizmo handle is hit). Answered from CPU data only, so they need no GL context and can be tested with
 * a hand-built [FrameSnapshot].
 */
interface SceneQueries {
    /** The scene's content with the previewed transforms applied: what the user sees now. */
    val content: SceneContent

    /** The scene's content as the file holds it (no preview). */
    val sceneContent: SceneContent

    /** Changes only when a model or terrain enters or leaves the drawn set, or one is replaced by another asset. */
    val drawnVersion: Long

    /** The entity under the pixel ([screenX], [screenY]) of a [width] x [height] view, or null. */
    fun pick(screenX: Int, screenY: Int, width: Int, height: Int): String?

    /** The ray through the pixel of a [width] x [height] view, or null for an empty view. */
    fun rayAt(screenX: Int, screenY: Int, width: Int, height: Int): Ray?

    /** The height of the highest surface under [entityId], or null when it cannot drop (terrain, the camera looked through, nothing below). */
    fun groundBelow(entityId: String): Float?

    /** The lowest world point of [entityId]'s box, or null when it is not drawn. */
    fun lowestPoint(entityId: String): Float?

    /** The gizmo of the selected entity for a view [height] pixels tall, or null when nothing is selected or it has no handles. */
    fun gizmoHandles(height: Int): GizmoHandles?

    /** The handle of the selected entity's gizmo under the pixel, or null. */
    fun gizmoHit(screenX: Int, screenY: Int, width: Int, height: Int): GizmoAxis?

    /** A drag of the [axis] handle of the selected entity's gizmo, started at the pixel; null when it cannot start. */
    fun beginDrag(axis: GizmoAxis, screenX: Int, screenY: Int, width: Int, height: Int): GizmoDrag?
}

/**
 * [SceneQueries] over the last [FrameSnapshot] (null before the first frame), the user's [state] and the scene's
 * content. The drawn boxes are put together once per snapshot and preview, not once per question.
 */
class SnapshotSceneQueries(
    private val snapshot: () -> FrameSnapshot?,
    private val state: SceneViewState,
    private val sceneContentOf: () -> SceneContent,
) : SceneQueries {
    override val sceneContent: SceneContent get() = sceneContentOf()

    private class DrawnBox(val id: String, val local: BoundingBox, val world: Matrix4) {
        fun oriented() = OrientedBox(local, world)
    }

    private class Targets(val boxes: List<DrawnBox>, val terrains: List<TerrainTarget>)

    private class Built(val snapshot: FrameSnapshot, val content: SceneContent, val viewCamera: String?, val preview: Map<String, DragResult>, val targets: Targets)

    private var built: Built? = null

    /** How many times the drawn boxes were put together; for tests. */
    internal var targetBuilds = 0
        private set

    private var contentOf: SceneContent? = null
    private var contentBase: SceneContent? = null
    private var contentPreview: Map<String, DragResult>? = null

    override val content: SceneContent
        get() {
            val base = sceneContent
            val preview = state.preview
            contentOf?.takeIf { base === contentBase && preview === contentPreview }?.let { return it }
            return (if (preview.isEmpty()) base else ScenePreview.apply(base, preview)).also {
                contentOf = it
                contentBase = base
                contentPreview = preview
            }
        }

    override val drawnVersion: Long get() = snapshot()?.drawnVersion ?: 0L

    private fun targets(): Targets? {
        val frame = snapshot() ?: return null
        val c = content
        built?.takeIf { it.snapshot === frame && it.content === c && it.viewCamera == state.viewCamera && it.preview === state.preview }
            ?.let { return it.targets }
        targetBuilds++
        val boxes = frame.boxes.map { b ->
            DrawnBox(b.id, b.local, state.preview[b.id]?.transform?.toMatrix() ?: b.world)
        } + SceneMarkers.targets(c, state.viewCamera).map { DrawnBox(it.entityId, it.bounds, Matrix4()) }
        return Targets(boxes, frame.terrains).also { built = Built(frame, c, state.viewCamera, state.preview, it) }
    }

    override fun pick(screenX: Int, screenY: Int, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val frame = snapshot() ?: return null
        val targets = targets() ?: return null
        val ray = ScenePicker.pickRay(frame.camera, screenX, screenY, width, height)
        return ScenePicker.pick(ray, targets.boxes.map { BoxTarget(it.id, BoundingBox(it.local).mul(it.world)) }, targets.terrains, frame.camera.far)
    }

    override fun rayAt(screenX: Int, screenY: Int, width: Int, height: Int): Ray? {
        val frame = snapshot() ?: return null
        return if (width <= 0 || height <= 0) null else ScenePicker.pickRay(frame.camera, screenX, screenY, width, height)
    }

    override fun groundBelow(entityId: String): Float? {
        if (entityId == state.viewCamera || content.terrains.any { it.entityId == entityId }) return null
        val targets = targets() ?: return null
        val footprint = targets.boxes.firstOrNull { it.id == entityId }?.oriented() ?: return null
        return ScenePicker.restHeight(footprint, targets.boxes.filter { it.id != entityId }.map { it.oriented() }, targets.terrains)
    }

    override fun lowestPoint(entityId: String): Float? = targets()?.boxes?.firstOrNull { it.id == entityId }?.oriented()?.bottom

    override fun gizmoHandles(height: Int): GizmoHandles? {
        val frame = snapshot() ?: return null
        return gizmoHandles(content, frame.camera, state, height)
    }

    override fun gizmoHit(screenX: Int, screenY: Int, width: Int, height: Int): GizmoAxis? {
        val handles = gizmoHandles(height) ?: return null
        val ray = rayAt(screenX, screenY, width, height) ?: return null
        return GizmoHit.find(ray, handles)
    }

    override fun beginDrag(axis: GizmoAxis, screenX: Int, screenY: Int, width: Int, height: Int): GizmoDrag? {
        val id = state.selectedId ?: return null
        val selected = ScenePreview.selected(content, id) ?: return null
        val ray = rayAt(screenX, screenY, width, height) ?: return null
        return GizmoDrag(state.gizmoMode, axis, selected.transform, ray, selected.direction).takeIf { it.isUsable }
    }

    companion object {
        /** The gizmo of the selected entity of [c], seen by [eyeCamera] in a view [height] pixels tall; shared with the renderer's drawing. */
        fun gizmoHandles(c: SceneContent, eyeCamera: PerspectiveCamera, state: SceneViewState, height: Int): GizmoHandles? {
            if (!state.gizmosEnabled) return null
            val id = state.selectedId ?: return null
            if (state.gizmoMode == GizmoMode.ROTATE && !canRotate(c, id)) return null
            val selected = ScenePreview.selected(c, id) ?: return null
            val eye = Vec3(eyeCamera.position.x, eyeCamera.position.y, eyeCamera.position.z)
            return GizmoHandles.of(selected.transform.position, state.gizmoMode, eye, eyeCamera.fieldOfView, height)
        }
    }
}
