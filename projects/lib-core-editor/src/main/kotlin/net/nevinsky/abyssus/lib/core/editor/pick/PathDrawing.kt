/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import kotlin.math.hypot

/** A path arrangement being drawn: the model [asset] to place and the [points] clicked so far. */
class PathDrawing(val asset: RenderAsset) {
    val points: MutableList<Vec3> = mutableListOf()
}

/** The points of the arrangement [entityId] while one of its handles is dragged, shown over the file's. */
data class PathEdit(val entityId: String, val points: List<Vec3>)

/** A handle of a selected arrangement's line, or the line itself. */
sealed interface PathHit {
    /** The handle on point [index]. */
    data class Point(val index: Int) : PathHit

    /** The handle in the middle of segment [segment] (from point `segment` to the next, or back to the first). */
    data class Middle(val segment: Int) : PathHit
}

/** A point on the screen, in the pixels of the view. */
data class ScreenPoint(val x: Float, val y: Float)

/** World points projected into a [width] x [height] view seen by [camera]. No GL. */
class ScreenProjection(private val camera: PerspectiveCamera, private val width: Int, private val height: Int) {
    private val scratch = Vector3()

    /** Where [p] is on the screen (origin top-left), or null behind the camera. */
    fun at(p: Vec3): ScreenPoint? {
        scratch.set(p.x - camera.position.x, p.y - camera.position.y, p.z - camera.position.z)
        if (scratch.dot(camera.direction) <= camera.near) return null
        scratch.set(p.x, p.y, p.z).prj(camera.combined)
        return ScreenPoint((scratch.x + 1f) / 2f * width, (1f - scratch.y) / 2f * height)
    }
}

/** The pixel radius in which a click grabs a handle, and in which it selects a line. */
const val PATH_HANDLE_PX = 12f
const val PATH_LINE_PX = 8f

/**
 * Hit tests for the line of an arrangement: the handle on each point, the handle in the middle of each segment, and
 * the segments themselves. Distances are in screen pixels, so a handle is as easy to grab far away as near.
 */
class PathHandles(private val points: List<Vec3>, private val closed: Boolean) {
    /** Number of segments: one fewer than the points, or as many when the line closes. */
    val segmentCount: Int get() = if (points.size < 2) 0 else if (closed && points.size > 2) points.size else points.size - 1

    /** The middle of segment [segment]. */
    fun middle(segment: Int): Vec3 {
        val a = points[segment]
        val b = points[(segment + 1) % points.size]
        return Vec3((a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.z + b.z) / 2f)
    }

    /** The handle under the pixel ([x], [y]), a point handle before a middle one; null when none is within [radius]. */
    fun hit(projection: ScreenProjection, x: Float, y: Float, radius: Float = PATH_HANDLE_PX): PathHit? {
        var best: PathHit? = null
        var bestDistance = radius
        for (i in points.indices) {
            val s = projection.at(points[i]) ?: continue
            val d = hypot(s.x - x, s.y - y)
            if (d <= bestDistance) {
                bestDistance = d
                best = PathHit.Point(i)
            }
        }
        if (best != null) return best
        for (i in 0 until segmentCount) {
            val s = projection.at(middle(i)) ?: continue
            val d = hypot(s.x - x, s.y - y)
            if (d <= bestDistance) {
                bestDistance = d
                best = PathHit.Middle(i)
            }
        }
        return best
    }

    /** The distance in pixels from ([x], [y]) to the nearest segment, or null when no segment is on screen. */
    fun lineDistance(projection: ScreenProjection, x: Float, y: Float): Float? {
        var best: Float? = null
        for (i in 0 until segmentCount) {
            val a = projection.at(points[i]) ?: continue
            val b = projection.at(points[(i + 1) % points.size]) ?: continue
            val d = distanceToSegment(x, y, a, b)
            if (best == null || d < best) best = d
        }
        return best
    }

    private fun distanceToSegment(x: Float, y: Float, a: ScreenPoint, b: ScreenPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length2 = dx * dx + dy * dy
        val t = if (length2 <= 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / length2).coerceIn(0f, 1f)
        return hypot(x - (a.x + dx * t), y - (a.y + dy * t))
    }
}
