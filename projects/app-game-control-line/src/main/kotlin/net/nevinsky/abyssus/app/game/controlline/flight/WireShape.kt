/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.physics.GRAVITY
import kotlin.math.max
import kotlin.math.sqrt

/** Segments of a drawn line. */
const val WIRE_SEGMENTS = 16

/** The least tension (N) the shape assumes, so a line with none still has a finite curve. */
private const val LEAST_TENSION = 0.02f

/**
 * The curve of one line from the handle to the plane (the shape of a string under load, design decision 4): the offset
 * from the straight line at fraction `u` of the length is `c * L² / T * (u - u^(n+2)) / ((n+1)(n+2))` for each load
 * `c * u^n` per metre: the wire's weight (`n = 0`, down) and its air drag (`n = 2`, as the wire's speed grows along it;
 * against the flight). Its centrifugal load acts along the line, so it changes the tension, not the shape. A slack line takes the tension that hangs it by the length
 * it has in excess of its chord.
 */
class WireShape(private val wire: Wire) {
    private val gravity = Vector3()
    private val offset = Vector3()
    private val drag = Vector3()

    /**
     * The [WIRE_SEGMENTS] + 1 points of a line of [rest] metres from [from] to [to] under [tension] N, with the plane
     * flying at [speed] along [heading] (unit), into [out].
     */
    fun points(
        from: Vector3, to: Vector3, rest: Float, tension: Float, speed: Float, heading: Vector3, out: List<Vector3>,
    ) {
        require(out.size == WIRE_SEGMENTS + 1) { "out holds ${WIRE_SEGMENTS + 1} points" }
        val chord = from.dst(to)
        val mu = wire.massPerMetre
        val hang = sqrt(3f * chord * max(0f, rest - chord) / 8f)
        val hung = if (hang > 1e-4f) mu * GRAVITY * chord * chord / (8f * hang) else 0f
        val t = max(max(tension, hung), LEAST_TENSION)

        gravity.set(0f, -mu * GRAVITY, 0f)
        drag.set(heading).scl(-0.5f * AIR_DENSITY * LINE_DRAG_COEFFICIENT * wire.diameter * speed * speed)
        val l2 = rest * rest / t
        for (i in 0..WIRE_SEGMENTS) {
            val u = i / WIRE_SEGMENTS.toFloat()
            val a = l2 * (u - u * u) / 2f
            val b = l2 * (u - u * u * u * u) / 12f
            offset.set(gravity).scl(a).mulAdd(drag, b)
            out[i].set(from).lerp(to, u).add(offset)
        }
    }
}
