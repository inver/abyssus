/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathSides
import net.nevinsky.abyssus.lib.core.util.azimuthOf
import net.nevinsky.abyssus.lib.core.util.wrapDegrees
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/** The most copies one arrangement may make. */
const val MAX_ARRANGEMENT_SLOTS = 1000

private const val EPS = 1e-4

/** One place for a copy: world [x] and [z], the line's height there as [fallbackY], and the turn about Y in degrees. */
data class PathSlot(val x: Float, val z: Float, val fallbackY: Float, val yawDegrees: Float) {
    /** Where the copy stands on [heights]. */
    fun position(heights: GroundHeight): Vector3 = Vector3(x, heights.heightAt(x, z, fallbackY), z)

    /** The turn about Y by [yawDegrees] with `w` not negative; libGDX returns the negated, equal rotation for negative angles. */
    fun rotation(): Quaternion {
        val q = Quaternion().setFromAxis(0f, 1f, 0f, yawDegrees)
        return if (q.w < 0f) {
            q.set(-q.x, -q.y, -q.z, -q.w)
        } else q
    }
}

/**
 * Places the copies of an arrangement: a pure function of the line and the settings of a [PathArrangementComponent],
 * with no terrain, JSON or GL. Distances are measured on the ground plane (X and Z).
 */
class PathLayout {
    private val curve = PathCurve()

    private data class Segment(val from: Vector3, val to: Vector3, val start: Double, val length: Double)

    /**
     * The slots of [arrangement] in slot order, or null when it would make more than [MAX_ARRANGEMENT_SLOTS]. A line
     * with fewer than two points, or a setting that is not usable (spacing not above 0, negative offset, a value that
     * is not finite), makes none.
     */
    fun slots(arrangement: PathArrangementComponent): List<PathSlot>? {
        val points = arrangement.points
        val spacing = arrangement.spacing.toDouble()
        if (!arrangement.isUsable()) return emptyList()

        // the copies follow the smooth line through the control points, so they are where the view draws it
        val sampled = curve.sample(pointsOf(arrangement), arrangement.closed)
        // a closed line of two points goes there and back
        val line = if (arrangement.closed && points.size == 2) {
            sampled + sampled.first()
        } else sampled
        val segments = segments(line, false)
        val length = segments.sumOf { it.length }
        val perStation = if (arrangement.sides == PathSides.BOTH) 2 else 1
        val stations = if (length <= 0.0) 1.0 else if (arrangement.closed) {
            ceil((length - EPS) / spacing)
        } else {
            floor((length + EPS) / spacing) + 1
        }
        if (stations * perStation > MAX_ARRANGEMENT_SLOTS) return null

        val out = ArrayList<PathSlot>((stations * perStation).toInt())
        for (i in 0 until stations.toInt()) {
            station(segments, i * spacing, arrangement, out)
        }
        return out
    }

    private fun segments(points: List<Vector3>, closed: Boolean): List<Segment> {
        val out = ArrayList<Segment>()
        var start = 0.0
        val count = if (closed) points.size else points.size - 1
        for (i in 0 until count) {
            val from = points[i]
            val to = points[(i + 1) % points.size]
            val length = hypot((to.x - from.x).toDouble(), (to.z - from.z).toDouble())
            if (length <= 0.0) continue
            out += Segment(from, to, start, length)
            start += length
        }
        return out
    }

    private fun station(
        segments: List<Segment>,
        distance: Double,
        a: PathArrangementComponent,
        out: MutableList<PathSlot>
    ) {
        val segment = segments.firstOrNull { distance < it.start + it.length - EPS } ?: segments.lastOrNull()
        val x: Double
        val z: Double
        val y: Double
        var dx = 1.0
        var dz = 0.0
        if (segment == null) {
            val p = a.points[0]
            x = p.x.toDouble(); z = p.z.toDouble(); y = p.y.toDouble()
        } else {
            val t = ((distance - segment.start) / segment.length).coerceIn(0.0, 1.0)
            dx = (segment.to.x - segment.from.x) / segment.length
            dz = (segment.to.z - segment.from.z) / segment.length
            x = segment.from.x + (segment.to.x - segment.from.x) * t
            z = segment.from.z + (segment.to.z - segment.from.z) * t
            y = segment.from.y + (segment.to.y - segment.from.y) * t
        }
        val heading = if (a.alignToPath) azimuthOf(dx, dz) else 0.0
        val offset = a.offset.toDouble()
        // left is up x direction
        val leftX = dz
        val leftZ = -dx
        fun add(side: Double, extraTurn: Double) {
            out += PathSlot(
                (x + leftX * offset * side).toFloat(),
                (z + leftZ * offset * side).toFloat(),
                y.toFloat(),
                wrapDegrees(heading + extraTurn + a.yawOffset).toFloat(),
            )
        }
        when (a.sides) {
            PathSides.CENTER -> add(0.0, 0.0)
            PathSides.LEFT -> add(1.0, 0.0)
            PathSides.RIGHT -> add(-1.0, 0.0)
            PathSides.BOTH -> {
                add(1.0, 0.0)
                add(-1.0, if (a.alignToPath) 180.0 else 0.0)
            }
        }
    }

}

/**
 * Whether the arrangement can make copies: a line of at least two finite points, a finite spacing above 0, a finite
 * offset of at least 0 and a finite yaw offset. Every entry point that creates or changes an arrangement checks this.
 */
fun PathArrangementComponent.isUsable(): Boolean {
    val spacing = spacing.toDouble()
    return points.size >= 2 && spacing.isFinite() && spacing > 0.0 && offset.isFinite() && offset >= 0f &&
            yawOffset.isFinite() && points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
}
