/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.editor.arrange.PATH_CURVE_STEPS
import net.nevinsky.abyssus.lib.core.editor.arrange.PathCurve
import kotlin.math.hypot

/** A path arrangement being drawn: the model [asset] to place and the [points] clicked so far. */
class PathDrawing(val asset: BaseMeta) {
    val points: MutableList<Vector3> = mutableListOf()
}

/** The points of the arrangement [entityId] while one of its handles is dragged, shown over the file's. */
data class PathEdit(val entityId: String, val points: List<Vector3>)

/** A handle of a selected arrangement's line, or the line itself. */
sealed interface PathHit {
    /** The handle on point [index]. */
    data class Point(val index: Int) : PathHit

    /** The curve itself, in [span] (from control point `span` to the next), at the world point [at] on it. */
    data class Line(val span: Int, val at: Vector3) : PathHit
}

/** World points projected into a [width] x [height] view seen by [camera]. No GL. */
class ScreenProjection(private val camera: PerspectiveCamera, private val width: Int, private val height: Int) {
    private val scratch = Vector3()

    /** Where [p] is on the screen (origin top-left), or null behind the camera. */
    fun at(p: Vector3): Vector2? {
        scratch.set(p.x - camera.position.x, p.y - camera.position.y, p.z - camera.position.z)
        if (scratch.dot(camera.direction) <= camera.near) {
            return null
        }
        scratch.set(p.x, p.y, p.z).prj(camera.combined)
        return Vector2((scratch.x + 1f) / 2f * width, (1f - scratch.y) / 2f * height)
    }
}

/** The pixel radius in which a click grabs a handle. */
const val PATH_HANDLE_PX = PATH_HANDLE_WIDTH_PX / 2f + 3f

/** The pixel radius in which a click meets the curve. */
const val PATH_LINE_PX = 8f

/**
 * Hit tests for the line of an arrangement: the handle on each point and the segments themselves. Distances are in screen pixels, so a handle is as easy to grab far away as near.
 */
class PathHandles(private val points: List<Vector3>, private val closed: Boolean) {
    private val curve = PathCurve()

    /**
     * The handle under the pixel ([x], [y]); else the curve if it is within [PATH_LINE_PX] pixels; else null. A handle is
     * hit when an edge of its diamond is within [radius] pixels (an edge of the drawn control object is what a click has to meet).
     */
    fun hit(projection: ScreenProjection, x: Float, y: Float, radius: Float = PATH_HANDLE_PX): PathHit? {
        var best: PathHit? = null
        var bestDistance = radius
        for (i in points.indices) {
            val d = edgeDistance(projection, points[i], PATH_POINT_HALF, x, y) ?: continue
            if (d <= bestDistance) {
                bestDistance = d
                best = PathHit.Point(i)
            }
        }
        return best ?: lineHit(projection, x, y)
    }

    /** The point of the curve nearest the pixel ([x], [y]) within [PATH_LINE_PX] pixels, with the span it is in. */
    private fun lineHit(projection: ScreenProjection, x: Float, y: Float): PathHit.Line? {
        var best: PathHit.Line? = null
        var bestDistance = PATH_LINE_PX
        for (span in 0 until curve.spanCount(points.size, closed)) {
            var from = curve.at(points, closed, span, 0f)
            for (step in 1..PATH_CURVE_STEPS) {
                val to = curve.at(points, closed, span, step.toFloat() / PATH_CURVE_STEPS)
                val sa = projection.at(from)
                val sb = projection.at(to)
                if (sa != null && sb != null) {
                    val t = along(x, y, sa, sb)
                    val d = hypot(x - (sa.x + (sb.x - sa.x) * t), y - (sa.y + (sb.y - sa.y) * t))
                    if (d <= bestDistance) {
                        bestDistance = d
                        best = PathHit.Line(span, Vector3(from).lerp(to, t))
                    }
                }
                from = to
            }
        }
        return best
    }

    /** The distance in pixels from ([x], [y]) to the nearest edge of the diamond at [at] that is on screen, or null when none is. */
    private fun edgeDistance(projection: ScreenProjection, at: Vector3, half: Float, x: Float, y: Float): Float? {
        var best: Float? = null
        for ((a, b) in diamondEdges(at, half)) {
            val sa = projection.at(a) ?: continue
            val sb = projection.at(b) ?: continue
            val d = distanceToSegment(x, y, sa, sb)
            if (best == null || d < best) best = d
        }
        return best
    }


    private fun distanceToSegment(x: Float, y: Float, a: Vector2, b: Vector2): Float {
        val t = along(x, y, a, b)
        return hypot(x - (a.x + (b.x - a.x) * t), y - (a.y + (b.y - a.y) * t))
    }

    /** How far along the screen segment from [a] to [b] the pixel ([x], [y]) projects: 0 at [a], 1 at [b]. */
    private fun along(x: Float, y: Float, a: Vector2, b: Vector2): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length2 = dx * dx + dy * dy
        return if (length2 <= 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / length2).coerceIn(0f, 1f)
    }
}
