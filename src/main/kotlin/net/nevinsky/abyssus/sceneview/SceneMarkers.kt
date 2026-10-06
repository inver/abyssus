/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Rgba
import net.nevinsky.abyssus.editor.content.LightKind
import net.nevinsky.abyssus.editor.content.LightPlacement
import net.nevinsky.abyssus.editor.content.CameraPlacement

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

private const val CAMERA_BOUNDS_HALF = 0.5f
private const val LIGHT_BOUNDS_HALF = 0.3f
private const val LIGHT_RADIUS = 0.3f
private const val DIRECTION_LINE_LENGTH = 2f

private val BODY_COLOR = Rgba(0.85f, 0.85f, 0.9f, 1f)
private val FRUSTUM_COLOR = Rgba(0.55f, 0.7f, 0.95f, 1f)

/**
 * The line geometry and pick bounds of the objects a scene places that have no model: cameras (a body and a frustum)
 * and lights (a small octahedron, with a direction line for directional and spot lights). No GL needed.
 */
class SceneMarkers {

    fun cameraBounds(position: Vec3): BoundingBox = boundsAround(position, CAMERA_BOUNDS_HALF)

    fun lightBounds(position: Vec3): BoundingBox = boundsAround(position, LIGHT_BOUNDS_HALF)

    private fun boundsAround(p: Vec3, half: Float) =
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

    fun drawCamera(out: LineSink, camera: CameraPlacement, positions: Map<String, Vec3>, aspect: Float) {
        val frustum = cameraFrustumOf(camera, positions, aspect)
        val c = frustum.corners
        for (ring in 0..1) for (i in 0 until 4) out.line(c[ring * 4 + i], c[ring * 4 + (i + 1) % 4], FRUSTUM_COLOR)
        for (i in 0 until 4) out.line(c[i], c[4 + i], FRUSTUM_COLOR)
        drawBody(out, camera.position, frustum.direction)
    }

    /** A box with a lens pyramid on the front, facing [direction]. */
    private fun drawBody(out: LineSink, position: Vec3, direction: Vec3) {
        val d = direction.toVector3()
        val (right, up) = cameraSideAxes(d)
        val eye = position.toVector3()
        fun at(r: Float, u: Float, f: Float) = Vector3(eye).mulAdd(right, r).mulAdd(up, u).mulAdd(d, f).toVec3()
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
        val color = Rgba(
            (light.color.r * 0.7f + 0.3f).coerceAtMost(1f),
            (light.color.g * 0.7f + 0.3f).coerceAtMost(1f),
            (light.color.b * 0.7f + 0.3f).coerceAtMost(1f),
            1f,
        )
        val r = LIGHT_RADIUS
        val tips = listOf(
            Vec3(p.x + r, p.y, p.z), Vec3(p.x - r, p.y, p.z),
            Vec3(p.x, p.y + r, p.z), Vec3(p.x, p.y - r, p.z),
            Vec3(p.x, p.y, p.z + r), Vec3(p.x, p.y, p.z - r),
        )
        // an octahedron: every tip joins every tip that is not opposite
        for (i in tips.indices) for (j in i + 1 until tips.size) if (i / 2 != j / 2) out.line(tips[i], tips[j], color)
        if (light.kind != LightKind.POINT) {
            val end = Vector3(p.x, p.y, p.z).mulAdd(light.direction.toVector3(), DIRECTION_LINE_LENGTH).toVec3()
            out.line(p, end, color)
        }
    }
}
