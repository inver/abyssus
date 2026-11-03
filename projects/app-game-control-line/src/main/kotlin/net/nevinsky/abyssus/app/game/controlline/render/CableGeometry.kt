/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * A control line to draw as a lit cable: a polyline of two or more [points] (world space; two for a straight line, more so
 * a later change can bend it), [diameter] metres thick, with the base [color] of its material.
 */
class Cable(val points: List<Vector3>, val diameter: Float, val color: Color) {
    init {
        require(points.size >= 2) { "a cable needs two points at least" }
    }
}

/** How many floats a cable vertex holds: position, then normal. */
const val CABLE_VERTEX_FLOATS = 6

/** The cable's radius on screen, in pixels, below which it would shimmer: it is thickened to this. */
const val CABLE_MIN_PIXEL_RADIUS = 0.75f

/** Sides of the tube: enough to read round at a pixel or two, few enough to cost nothing. */
const val CABLE_SIDES = 6

/**
 * The triangles of a cable (no GL): a tube of [sides] around a polyline, a ring of vertices at each point (position and
 * unit outward normal), joined by two triangles a side, counter-clockwise seen from outside. The ends are open: at a pixel
 * wide nobody sees into them.
 */
class CableGeometry(private val sides: Int = CABLE_SIDES) {
    init {
        require(sides >= 3) { "a tube needs three sides at least" }
    }

    private val tangent = Vector3()
    private val normal = Vector3()
    private val binormal = Vector3()
    private val ring = Vector3()

    fun vertexCount(points: Int): Int = points * sides

    fun indexCount(points: Int): Int = (points - 1) * sides * 6

    /**
     * The world radius that keeps a cable [wireRadius] thick at least [CABLE_MIN_PIXEL_RADIUS] pixels thick on screen at
     * [distance] from a camera with vertical field of view [fovDegrees] on a viewport [viewportHeight] pixels tall.
     */
    fun radiusAt(wireRadius: Float, distance: Float, fovDegrees: Float, viewportHeight: Float): Float {
        val metresPerPixel = 2f * distance * tan(fovDegrees * (PI.toFloat() / 360f)) / viewportHeight
        return max(wireRadius, CABLE_MIN_PIXEL_RADIUS * metresPerPixel)
    }

    /**
     * Writes the tube around [points] with the radius [radii] of each point: its vertices to [vertices] from the vertex
     * [firstVertex] ([CABLE_VERTEX_FLOATS] floats each) and its triangle indices to [indices] from [indexStart], which refer
     * to [firstVertex] onward. Returns the number of indices written.
     */
    fun build(
        points: List<Vector3>, radii: FloatArray, vertices: FloatArray, firstVertex: Int, indices: ShortArray, indexStart: Int,
    ): Int {
        require(points.size >= 2 && radii.size >= points.size) { "a radius for each of two points at least" }
        for (i in points.indices) {
            tangentAt(points, i)
            frame()
            for (s in 0 until sides) {
                val angle = 2f * PI.toFloat() * s / sides
                val c = cos(angle)
                val sn = sin(angle)
                ring.set(normal).scl(c).mulAdd(binormal, sn)
                val at = (firstVertex + i * sides + s) * CABLE_VERTEX_FLOATS
                vertices[at] = points[i].x + ring.x * radii[i]
                vertices[at + 1] = points[i].y + ring.y * radii[i]
                vertices[at + 2] = points[i].z + ring.z * radii[i]
                vertices[at + 3] = ring.x
                vertices[at + 4] = ring.y
                vertices[at + 5] = ring.z
            }
        }
        var n = indexStart
        for (i in 0 until points.size - 1) {
            for (s in 0 until sides) {
                val a = firstVertex + i * sides + s
                val b = firstVertex + i * sides + (s + 1) % sides
                val c = firstVertex + (i + 1) * sides + s
                val d = firstVertex + (i + 1) * sides + (s + 1) % sides
                indices[n++] = a.toShort(); indices[n++] = b.toShort(); indices[n++] = c.toShort()
                indices[n++] = b.toShort(); indices[n++] = d.toShort(); indices[n++] = c.toShort()
            }
        }
        return n - indexStart
    }

    /** The direction of the polyline at point [i]: along the segment at an end, across the corner between two segments. */
    private fun tangentAt(points: List<Vector3>, i: Int) {
        val before = points[max(i - 1, 0)]
        val after = points[minOf(i + 1, points.size - 1)]
        tangent.set(after).sub(before)
        if (tangent.isZero(1e-9f)) tangent.set(0f, 0f, 1f) else tangent.nor()
    }

    /** A right-handed frame (normal x binormal = tangent) around [tangent], steady for a line of any direction. */
    private fun frame() {
        val reference = if (kotlin.math.abs(tangent.y) > 0.99f) Vector3.X else Vector3.Y
        normal.set(reference).crs(tangent).nor()
        binormal.set(tangent).crs(normal)
    }
}
