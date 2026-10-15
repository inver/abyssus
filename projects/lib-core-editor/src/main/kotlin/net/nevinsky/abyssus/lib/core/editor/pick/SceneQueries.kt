/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.Quat
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.water.ValidWater
import net.nevinsky.abyssus.lib.core.editor.water.WaterHit
import net.nevinsky.abyssus.lib.core.editor.water.WaterPreview
import net.nevinsky.abyssus.lib.core.editor.arrange.pointsOf

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray

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

    /**
     * What is under the pixel: the entity [pick] finds, or a water surface when one is met nearer than that entity (or
     * nothing is). A water rectangle never wins over nearer terrain or a model.
     */
    fun pickTarget(screenX: Int, screenY: Int, width: Int, height: Int): SceneTarget? =
        pick(screenX, screenY, width, height)?.let(SceneTarget::Entity)

    /** The ray through the pixel of a [width] x [height] view, or null for an empty view. */
    fun rayAt(screenX: Int, screenY: Int, width: Int, height: Int): Ray?

    /** The drawn terrain of [entityId]: its data and world matrix, or null when it is not drawn. */
    fun terrainOf(entityId: String): TerrainTarget?

    /**
     * The nearest terrain hit along [ray], when it belongs to [entityId]; null when another terrain covers it.
     * Model boxes do not block the brush, and the camera's far plane limits the ray.
     */
    fun terrainHit(entityId: String, ray: Ray): Vector3?

    /** The nearest point where [ray] meets any drawn terrain, or null when it meets none (a click in the sky). */
    fun groundHit(ray: Ray): Vector3?

    /** The points of the arrangement [entityId] as shown now: the ones being dragged, else the file's; null when it is none. */
    fun pathPoints(entityId: String): List<Vec3>?

    /** The handle of the selected arrangement's line under the pixel, or null. */
    fun pathHandleAt(screenX: Int, screenY: Int, width: Int, height: Int): PathHit?

    /** The arrangement whose line passes within a few pixels of the pixel, or null. */
    fun pathLineAt(screenX: Int, screenY: Int, width: Int, height: Int): String?

    /** How many pixels the pixel ([screenX], [screenY]) is from where the world point [at] is drawn; null when it is off screen. */
    fun screenDistance(at: Vec3, screenX: Int, screenY: Int, width: Int, height: Int): Float?

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
    private var contentWaterPreview: Map<String, Vec3>? = null

    override val content: SceneContent
        get() {
            val base = sceneContent
            val preview = state.preview
            val waterPreview = state.waterPreview
            contentOf?.takeIf { base === contentBase && preview === contentPreview && waterPreview === contentWaterPreview }
                ?.let { return it }
            val posed = if (preview.isEmpty()) base else ScenePreview().apply(base, preview)
            return (if (waterPreview.isEmpty()) posed else posed.copy(water = WaterPreview().apply(posed.water, waterPreview))).also {
                contentOf = it
                contentBase = base
                contentPreview = preview
                contentWaterPreview = waterPreview
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
        } + SceneMarkers().targets(c, state.viewCamera).map { DrawnBox(it.entityId, it.bounds, Matrix4()) }
        return Targets(boxes, frame.terrains).also { built = Built(frame, c, state.viewCamera, state.preview, it) }
    }

    override fun pick(screenX: Int, screenY: Int, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val frame = snapshot() ?: return null
        val targets = targets() ?: return null
        val ray = ScenePicker().pickRay(frame.camera, screenX, screenY, width, height)
        return ScenePicker().pick(ray, targets.boxes.map { BoxTarget(it.id, BoundingBox(it.local).mul(it.world)) }, targets.terrains, frame.camera.far)
    }

    override fun pickTarget(screenX: Int, screenY: Int, width: Int, height: Int): SceneTarget? {
        if (width <= 0 || height <= 0) return null
        val frame = snapshot() ?: return null
        val targets = targets() ?: return null
        val ray = ScenePicker().pickRay(frame.camera, screenX, screenY, width, height)
        val entity = ScenePicker().nearest(ray, targets.boxes.map { BoxTarget(it.id, BoundingBox(it.local).mul(it.world)) }, targets.terrains, frame.camera.far)
        val water = WaterHit().nearest(ray, content.water.filterIsInstance<ValidWater>().map { it.settings }, frame.camera.far)
        return when {
            water != null && (entity == null || water.distance < entity.second) -> SceneTarget.Water(water.id)
            entity != null -> SceneTarget.Entity(entity.first)
            else -> null
        }
    }

    override fun rayAt(screenX: Int, screenY: Int, width: Int, height: Int): Ray? {
        val frame = snapshot() ?: return null
        return if (width <= 0 || height <= 0) null else ScenePicker().pickRay(frame.camera, screenX, screenY, width, height)
    }

    override fun terrainOf(entityId: String): TerrainTarget? =
        snapshot()?.terrains?.firstOrNull { it.entityId == entityId }

    override fun terrainHit(entityId: String, ray: Ray): Vector3? {
        val frame = snapshot() ?: return null
        val picker = ScenePicker()
        var nearest: TerrainTarget? = null
        var nearestDistance = Float.POSITIVE_INFINITY
        for (terrain in frame.terrains) {
            val distance = picker.terrainDistance(ray, terrain, frame.camera.far) ?: continue
            if (distance < nearestDistance) {
                nearest = terrain
                nearestDistance = distance
            }
        }
        if (nearest?.entityId != entityId) return null
        return Vector3(ray.origin).mulAdd(ray.direction, nearestDistance)
    }

    override fun groundHit(ray: Ray): Vector3? {
        val frame = snapshot() ?: return null
        val picker = ScenePicker()
        val nearest = frame.terrains.mapNotNull { picker.terrainDistance(ray, it, frame.camera.far) }.minOrNull() ?: return null
        return Vector3(ray.origin).mulAdd(ray.direction, nearest)
    }

    override fun pathPoints(entityId: String): List<Vec3>? {
        state.pathEdit?.takeIf { it.entityId == entityId }?.let { return it.points }
        return content.arrangements.firstOrNull { it.entityId == entityId }?.arrangement?.let(::pointsOf)
    }

    override fun pathHandleAt(screenX: Int, screenY: Int, width: Int, height: Int): PathHit? {
        val frame = snapshot() ?: return null
        val id = state.selectedId ?: return null
        val arrangement = content.arrangements.firstOrNull { it.entityId == id } ?: return null
        val points = pathPoints(id) ?: return null
        if (width <= 0 || height <= 0) return null
        return PathHandles(points, arrangement.arrangement.closed)
            .hit(ScreenProjection(frame.camera, width, height), screenX.toFloat(), screenY.toFloat())
    }

    override fun pathLineAt(screenX: Int, screenY: Int, width: Int, height: Int): String? {
        val frame = snapshot() ?: return null
        if (width <= 0 || height <= 0) return null
        val projection = ScreenProjection(frame.camera, width, height)
        var best: String? = null
        var bestDistance = PATH_LINE_PX
        for (placement in content.arrangements) {
            val points = pathPoints(placement.entityId) ?: continue
            val d = PathHandles(points, placement.arrangement.closed).lineDistance(projection, screenX.toFloat(), screenY.toFloat()) ?: continue
            if (d <= bestDistance) {
                bestDistance = d
                best = placement.entityId
            }
        }
        return best
    }

    override fun screenDistance(at: Vec3, screenX: Int, screenY: Int, width: Int, height: Int): Float? {
        val frame = snapshot() ?: return null
        if (width <= 0 || height <= 0) return null
        val s = ScreenProjection(frame.camera, width, height).at(at) ?: return null
        return kotlin.math.hypot(s.x - screenX, s.y - screenY)
    }

    override fun groundBelow(entityId: String): Float? {
        if (entityId == state.viewCamera || content.terrains.any { it.entityId == entityId }) return null
        val targets = targets() ?: return null
        val footprint = targets.boxes.firstOrNull { it.id == entityId }?.oriented() ?: return null
        return ScenePicker().restHeight(footprint, targets.boxes.filter { it.id != entityId }.map { it.oriented() }, targets.terrains)
    }

    override fun lowestPoint(entityId: String): Float? = targets()?.boxes?.firstOrNull { it.id == entityId }?.oriented()?.bottom

    override fun gizmoHandles(height: Int): GizmoHandles? {
        val frame = snapshot() ?: return null
        return gizmoHandlesFor(content, frame.camera, state, height)
    }

    override fun gizmoHit(screenX: Int, screenY: Int, width: Int, height: Int): GizmoAxis? {
        val handles = gizmoHandles(height) ?: return null
        val ray = rayAt(screenX, screenY, width, height) ?: return null
        return GizmoHit().find(ray, handles)
    }

    /** A drag of the move handle [axis] of water surface [waterId]; its transform is the surface centre, unrotated. */
    private fun beginWaterDrag(waterId: String, axis: GizmoAxis, screenX: Int, screenY: Int, width: Int, height: Int): GizmoDrag? {
        val centre = waterCentre(content, waterId) ?: return null
        val ray = rayAt(screenX, screenY, width, height) ?: return null
        val start = PlacementTransform(centre, Quat.IDENTITY, Vec3(1f, 1f, 1f))
        return GizmoDrag(GizmoMode.MOVE, axis, start, ray, null, axis.direction, state.snap).takeIf { it.isUsable }
    }

    override fun beginDrag(axis: GizmoAxis, screenX: Int, screenY: Int, width: Int, height: Int): GizmoDrag? {
        state.selectedWater?.let { return beginWaterDrag(it, axis, screenX, screenY, width, height) }
        val id = state.selectedId ?: return null
        val selected = ScenePreview().selected(content, id) ?: return null
        val ray = rayAt(screenX, screenY, width, height) ?: return null
        val axes = gizmoAxes(state.gizmoSpace, selected.transform.rotation)
        return GizmoDrag(state.gizmoMode, axis, selected.transform, ray, selected.direction, axes.getValue(axis), state.snap).takeIf { it.isUsable }
    }
}

/** The gizmo of the selected entity of [c], seen by [eyeCamera] in a view [height] pixels tall; shared with the renderer's drawing. */
fun gizmoHandlesFor(c: SceneContent, eyeCamera: PerspectiveCamera, state: SceneViewState, height: Int): GizmoHandles? {
    if (!state.gizmosEnabled) return null
    // Paint Foliage mode hides the move and rotate gizmos of the painted terrain.
    if (state.paint != null) return null
    // Select mode highlights the selection without handles.
    if (state.gizmoMode == GizmoMode.SELECT) return null
    // Water only moves: it has no rotation to turn, and its handles follow the world axes.
    state.selectedWater?.let { waterId ->
        if (state.gizmoMode != GizmoMode.MOVE) return null
        val centre = waterCentre(c, waterId) ?: return null
        val eye = Vec3(eyeCamera.position.x, eyeCamera.position.y, eyeCamera.position.z)
        return GizmoHandles(centre, GizmoMode.MOVE, eye, eyeCamera.fieldOfView, height, WORLD_AXES)
    }
    val id = state.selectedId ?: return null
    if (state.gizmoMode == GizmoMode.ROTATE && !canRotate(c, id)) return null
    val selected = ScenePreview().selected(c, id) ?: return null
    val eye = Vec3(eyeCamera.position.x, eyeCamera.position.y, eyeCamera.position.z)
    val axes = gizmoAxes(state.gizmoSpace, selected.transform.rotation)
    return GizmoHandles(selected.transform.position, state.gizmoMode, eye, eyeCamera.fieldOfView, height, axes)
}


/** The centre of the valid water surface [waterId] of [c], or null when the scene has no such drawable surface. */
fun waterCentre(c: SceneContent, waterId: String): Vec3? =
    (c.water.firstOrNull { it.id == waterId } as? ValidWater)?.settings?.let { Vec3(it.x.toFloat(), it.level.toFloat(), it.z.toFloat()) }
