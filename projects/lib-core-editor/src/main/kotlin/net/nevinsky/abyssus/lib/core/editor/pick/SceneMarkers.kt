/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.arrange.PathCurve
import net.nevinsky.abyssus.lib.core.editor.arrange.PathLayout
import net.nevinsky.abyssus.lib.core.editor.arrange.pointsOf
import net.nevinsky.abyssus.lib.core.editor.content.CameraPlacement
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement
import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.core.editor.scene.*

private const val CAMERA_BOUNDS_HALF = 0.5f
private const val LIGHT_BOUNDS_HALF = 0.3f
private const val LIGHT_RADIUS = 0.3f
private const val DIRECTION_LINE_LENGTH = 2f

private val BODY_COLOR = Color(0.85f, 0.85f, 0.9f, 1f)
private const val SLOT_MARKER_HALF = 0.4f
private const val SLOT_MARKER_HEIGHT = 1.2f
/** The half-size of the diamond on a control point, and of the smaller one halfway along a segment (world units). */
const val PATH_POINT_HALF = 0.5f

/** How wide the edges of a control object are drawn, in screen pixels. */
const val PATH_HANDLE_WIDTH_PX = 3f

/** The 12 edges of the diamond (octahedron) of half-size [r] at [p]; every tip joins every tip that is not opposite. */
fun diamondEdges(p: Vector3, r: Float): List<Pair<Vector3, Vector3>> {
    val tips = listOf(
        Vector3(p.x + r, p.y, p.z), Vector3(p.x - r, p.y, p.z), Vector3(p.x, p.y + r, p.z),
        Vector3(p.x, p.y - r, p.z), Vector3(p.x, p.y, p.z + r), Vector3(p.x, p.y, p.z - r),
    )
    return buildList { for (i in tips.indices) for (j in i + 1 until tips.size) if (i / 2 != j / 2) add(tips[i] to tips[j]) }
}

private val PATH_COLOR = Color(1f, 0.8f, 0.2f, 1f)
private val SLOT_COLOR = Color(0.4f, 0.9f, 0.4f, 1f)
private val POINT_HANDLE_COLOR = Color(1f, 1f, 1f, 1f)
private val SELECTED_HANDLE_COLOR = Color(1f, 0.35f, 0.2f, 1f)

/** How wide the line of a path arrangement is drawn, in screen pixels. */
const val PATH_WIDTH_PX = 4f
private val FRUSTUM_COLOR = Color(0.55f, 0.7f, 0.95f, 1f)

/**
 * The line geometry and pick bounds of the objects a scene places that have no model: cameras (a body and a frustum)
 * and lights (a small octahedron, with a direction line for directional and spot lights). No GL needed.
 */
class SceneMarkers {
    private val curve = PathCurve()


    fun cameraBounds(position: Vector3): BoundingBox = boundsAround(position, CAMERA_BOUNDS_HALF)

    fun lightBounds(position: Vector3): BoundingBox = boundsAround(position, LIGHT_BOUNDS_HALF)

    private fun boundsAround(p: Vector3, half: Float) =
        BoundingBox(Vector3(p.x - half, p.y - half, p.z - half), Vector3(p.x + half, p.y + half, p.z + half))

    /** Pick targets for every camera and light except the camera [skipCamera] (the one being looked through). */
    fun targets(content: SceneContent, skipCamera: String? = null): List<BoxTarget> =
        content.cameras.filter { it.entityId != skipCamera }.map { BoxTarget(it.entityId, cameraBounds(it.position)) } +
            content.lights.map { BoxTarget(it.entityId, lightBounds(it.position)) }

    /** The bounds of the camera or light [entityId], or null when it is neither. */
    fun boundsOf(content: SceneContent, entityId: String): BoundingBox? =
        content.cameras.firstOrNull { it.entityId == entityId }?.let { cameraBounds(it.position) }
            ?: content.lights.firstOrNull { it.entityId == entityId }?.let { lightBounds(it.position) }

    /** Draws every marker of [content] into [out], for a viewport of [aspect], leaving out the camera [skipCamera]. */
    fun draw(out: LineSink, content: SceneContent, aspect: Float, skipCamera: String? = null) {
        for (camera in content.cameras) {
            if (camera.entityId == skipCamera) continue
            drawCamera(out, camera, content.entityPositions, aspect)
        }
        for (light in content.lights) drawLight(out, light)
    }

    fun drawCamera(out: LineSink, camera: CameraPlacement, positions: Map<String, Vector3>, aspect: Float) {
        val frustum = cameraFrustumOf(camera, positions, aspect)
        val c = frustum.corners
        for (ring in 0..1) for (i in 0 until 4) out.line(c[ring * 4 + i], c[ring * 4 + (i + 1) % 4], FRUSTUM_COLOR)
        for (i in 0 until 4) out.line(c[i], c[4 + i], FRUSTUM_COLOR)
        drawBody(out, camera.position, frustum.direction)
    }

    /** A box with a lens pyramid on the front, facing [direction]. */
    private fun drawBody(out: LineSink, position: Vector3, direction: Vector3) {
        val d = direction.cpy()
        val (right, up) = cameraSideAxes(d)
        val eye = position.cpy()
        fun at(r: Float, u: Float, f: Float) = Vector3(eye).mulAdd(right, r).mulAdd(up, u).mulAdd(d, f).cpy()
        val w = 0.3f
        val h = 0.2f
        val l = 0.3f
        val corners = listOf(
            at(-w, -h, -l), at(w, -h, -l), at(w, h, -l), at(-w, h, -l),
            at(-w, -h, l), at(w, -h, l), at(w, h, l), at(-w, h, l),
        )
        for (ring in 0..1) for (i in 0 until 4) out.line(corners[ring * 4 + i], corners[ring * 4 + (i + 1) % 4], BODY_COLOR)
        for (i in 0 until 4) out.line(corners[i], corners[4 + i], BODY_COLOR)
        val tip = at(0f, 0f, l + 0.3f)
        for (i in 4 until 8) out.line(corners[i], tip, BODY_COLOR)
    }

    fun drawLight(out: LineSink, light: LightPlacement) {
        val p = light.position
        val color = Color(
            (light.color.r * 0.7f + 0.3f).coerceAtMost(1f),
            (light.color.g * 0.7f + 0.3f).coerceAtMost(1f),
            (light.color.b * 0.7f + 0.3f).coerceAtMost(1f),
            1f,
        )
        val r = LIGHT_RADIUS
        val tips = listOf(
            Vector3(p.x + r, p.y, p.z), Vector3(p.x - r, p.y, p.z),
            Vector3(p.x, p.y + r, p.z), Vector3(p.x, p.y - r, p.z),
            Vector3(p.x, p.y, p.z + r), Vector3(p.x, p.y, p.z - r),
        )
        // an octahedron: every tip joins every tip that is not opposite
        for (i in tips.indices) for (j in i + 1 until tips.size) if (i / 2 != j / 2) out.line(tips[i], tips[j], color)
        if (light.kind != LightKind.POINT) {
            val end = Vector3(p.x, p.y, p.z).mulAdd(light.direction.cpy(), DIRECTION_LINE_LENGTH).cpy()
            out.line(p, end, color)
        }
    }

    /**
     * Draws a path arrangement's line through its points (and back to the first when it is closed), a cross with a short
     * post for each copy at its height on [ground], and, when [handles] is set, a diamond on each point and a small
     * cross in the middle of each segment. The copies are laid out by [PathLayout], so they are where regeneration would
     * put them (the crosses are left out when [slots] is false). No model is drawn.
     */
    fun drawPath(
        out: LineSink,
        path: PathArrangementComponent,
        ground: GroundHeight,
        handles: Boolean,
        slots: Boolean = true,
        selectedPoint: Int? = null,
    ) {
        val points = pointsOf(path)
        val line = curve.sample(points, path.closed)
        for (i in 0 until line.size - 1) out.thickLine(line[i], line[i + 1], PATH_COLOR, PATH_WIDTH_PX)
        if (slots) for (slot in PathLayout().slots(path).orEmpty()) {
            val y = ground.heightAt(slot.x, slot.z, slot.fallbackY)
            val r = SLOT_MARKER_HALF
            out.line(Vector3(slot.x - r, y, slot.z), Vector3(slot.x + r, y, slot.z), SLOT_COLOR)
            out.line(Vector3(slot.x, y, slot.z - r), Vector3(slot.x, y, slot.z + r), SLOT_COLOR)
            out.line(Vector3(slot.x, y, slot.z), Vector3(slot.x, y + SLOT_MARKER_HEIGHT, slot.z), SLOT_COLOR)
        }
        if (!handles) return
        for ((i, p) in points.withIndex()) drawDiamond(out, p, PATH_POINT_HALF, if (i == selectedPoint) SELECTED_HANDLE_COLOR else POINT_HANDLE_COLOR, PATH_HANDLE_WIDTH_PX)
    }

    private fun drawDiamond(out: LineSink, p: Vector3, r: Float, color: Color, widthPx: Float) {
        for ((a, b) in diamondEdges(p, r)) out.thickLine(a, b, color, widthPx)
    }
}
