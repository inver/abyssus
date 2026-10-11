/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathPoint
import net.nevinsky.abyssus.lib.core.ecs.component.PathSides
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/** The most copies one arrangement may make. */
const val MAX_ARRANGEMENT_SLOTS = 1000

private const val EPS = 1e-4

/** One place for a copy: world [x] and [z], the line's height there as [fallbackY], and the turn about Y in degrees. */
data class PathSlot(val x: Float, val z: Float, val fallbackY: Float, val yawDegrees: Float)

/**
 * Places the copies of an arrangement: a pure function of the line and the settings of a [PathArrangementComponent],
 * with no terrain, JSON or GL. Distances are measured on the ground plane (X and Z).
 */
class PathLayout {
    private data class Segment(val from: PathPoint, val to: PathPoint, val start: Double, val length: Double)

    /**
     * The slots of [arrangement] in slot order, or null when it would make more than [MAX_ARRANGEMENT_SLOTS]. A line
     * with fewer than two points, or a setting that is not usable (spacing not above 0, negative offset, a value that
     * is not finite), makes none.
     */
    fun slots(arrangement: PathArrangementComponent): List<PathSlot>? {
        val points = arrangement.points
        val spacing = arrangement.spacing.toDouble()
        val usable = points.size >= 2 && spacing.isFinite() && spacing > 0.0 && arrangement.offset.isFinite() &&
            arrangement.offset >= 0f && arrangement.yawOffset.isFinite() &&
            points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
        if (!usable) return emptyList()

        val segments = segments(points, arrangement.closed)
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

    private fun segments(points: List<PathPoint>, closed: Boolean): List<Segment> {
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

    private fun station(segments: List<Segment>, distance: Double, a: PathArrangementComponent, out: MutableList<PathSlot>) {
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
        val heading = if (a.alignToPath) Math.toDegrees(atan2(-dz, dx)) else 0.0
        val offset = a.offset.toDouble()
        // left is up x direction
        val leftX = dz
        val leftZ = -dx
        fun add(side: Double, extraTurn: Double) {
            out += PathSlot(
                (x + leftX * offset * side).toFloat(),
                (z + leftZ * offset * side).toFloat(),
                y.toFloat(),
                wrap(heading + extraTurn + a.yawOffset).toFloat(),
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

    /** [degrees] turned into (-180, 180]. */
    private fun wrap(degrees: Double): Double {
        var d = degrees % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

}
