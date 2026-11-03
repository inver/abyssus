/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.track.arcLength
import net.nevinsky.abyssus.app.game.controlline.track.dropEnd
import net.nevinsky.abyssus.app.game.controlline.track.dropStart
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** The step (m) of a fillet's points. */
private const val FILLET_STEP = 0.1f

/**
 * [edges] (each a polyline of unit vectors, one starting where the last ends) joined by circular turns of [corners]
 * metres radius on a hemisphere of [radius] metres: each corner cuts `r tan(θ/2)` from the edges either side, at most
 * 45% of either, and turns at a steady rate between the cuts. Unlike a quadratic rounding, a wide turn keeps its radius,
 * so a plane that turns no tighter than that can fly it.
 */
fun filleted(edges: List<List<Vector3>>, corners: List<Float>, radius: Float): List<Vector3> {
    require(corners.size == edges.size - 1) { "one corner per junction" }
    val cut = edges.map { it.map(::Vector3) }.toMutableList()
    val arcs = HashMap<Int, List<Vector3>>()
    for (j in 0 until edges.size - 1) {
        val a = cut[j]
        val b = cut[j + 1]
        val vertex = a.last()
        val tIn = tangentAt(vertex, Vector3(vertex).sub(a[a.size - 2]))
        val tOut = tangentAt(vertex, Vector3(b[1]).sub(vertex))
        val theta = turn(tIn, tOut, vertex)
        if (abs(theta) < 0.02f) continue
        val r = corners[j]
        val d = min(r * tan(abs(theta) / 2f), 0.45f * min(arcLength(a), arcLength(b)) * radius) / radius
        val trimmedA = dropEnd(a, d)
        val trimmedB = dropStart(b, d)
        val start = trimmedA.last()
        val heading = tangentAt(start, Vector3(start).sub(trimmedA[max(0, trimmedA.size - 2)]).let { if (it.len2() < 1e-12f) tIn else it })
        // the turn that brings the heading round by theta over the cut's two sides
        val length = 2f * d * radius / (2f * tan(abs(theta) / 2f)) * abs(theta)
        arcs[j] = arc(start, heading, theta / length, length, radius)
        cut[j] = trimmedA
        cut[j + 1] = trimmedB
    }
    val out = ArrayList<Vector3>()
    for (j in edges.indices) {
        out += cut[j]
        arcs[j]?.let { out += it }
    }
    return out
}

/** The points of a steady turn from [start] heading [heading], of [curvature] (1/m, positive left) for [length] metres. */
private fun arc(start: Vector3, heading: Vector3, curvature: Float, length: Float, radius: Float): List<Vector3> {
    val n = max(2, ceil(length / FILLET_STEP).toInt())
    val step = length / n
    val p = Vector3(start)
    val h = Vector3(heading)
    val out = ArrayList<Vector3>(n)
    repeat(n) {
        // turn, then move along the great circle of the heading
        h.rotateRad(p, curvature * step)
        val angle = step / radius
        val next = Vector3(p).scl(cos(angle)).mulAdd(h, sin(angle)).nor()
        h.scl(cos(angle)).mulAdd(p, -sin(angle)).nor()
        p.set(next)
        out += Vector3(p)
    }
    return out
}
