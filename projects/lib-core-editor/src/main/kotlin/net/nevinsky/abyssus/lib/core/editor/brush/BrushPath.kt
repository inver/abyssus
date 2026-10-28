/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.brush

import net.nevinsky.abyssus.lib.core.util.smooth01
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.*

/** The smoothstep of a brush falloff `1 - d / radius`: 0 at the radius, 1 at the centre, flat at both ends. */
fun smoothFalloff(falloff: Float): Float = smooth01(falloff)

/**
 * The walk and footprint every terrain brush shares (Paint Foliage and Sculpt Terrain): stamps along the world-space
 * drag path at intervals of a quarter of the radius, and the grid vertices within the radius of a stamp.
 *
 * The grid is [resolution] points over a terrain [size] world units wide, in terrain-local space: a stamp's centre goes
 * through the inverse of the entity's [world] matrix, and the distance is measured through the entity's scale along the
 * terrain's own axes. For the translation, yaw rotation and scaling terrain entities are built from, that is exactly
 * the world-space distance on the terrain plane, so a rotated or scaled entity is brushed under the circle the view draws.
 *
 * One stroke is one instance: [walk] is called once per drag segment and keeps the stamp spacing across the calls, so a
 * spot is stamped once however finely the drag is sampled. Pure math that touches no `Gdx.*`.
 */
class BrushPath(private val size: Int, private val resolution: Int, world: Matrix4) {
    private val toLocal = Matrix4(world).inv()

    /** The world length of one terrain-local x (and z) unit: the entity's scale along the terrain's own axes. */
    val scaleX = Vector3(1f, 0f, 0f).rot(world).len()
    val scaleZ = Vector3(0f, 0f, 1f).rot(world).len()

    private val point = Vector3()
    private val local = Vector3()

    /** Distance since the last stamp, in world units; the press itself is always a stamp. */
    private var sinceLastStamp = 0f
    private var pressed = false

    /** The distance between two grid points, in terrain-local units. */
    val cell: Float = size.toFloat() / (resolution - 1)

    init {
        require(resolution >= 2) { "a brush grid needs at least 2 points" }
    }

    /** True while the entity's scale and the given [radius] let a brush stamp at all. */
    fun canStamp(radius: Float): Boolean = radius > 0f && scaleX > 0f && scaleZ > 0f

    /**
     * Stamps along the segment from [from] to [to] (world-space stroke points): [stamp] is called with each stamp's
     * world centre, which it must not keep. The first call of a stroke always stamps at [from].
     */
    fun walk(from: Vector3, to: Vector3, radius: Float, stamp: (Vector3) -> Unit) {
        if (!canStamp(radius)) return
        if (!pressed) {
            pressed = true
            sinceLastStamp = 0f
            stamp(point.set(from))
        }
        val spacing = radius / 4f
        val distance = from.dst(to)
        var along = spacing - sinceLastStamp
        var lastStamp = Float.NaN
        while (along <= distance) {
            lastStamp = along
            stamp(point.set(from).lerp(to, along / distance))
            along += spacing
        }
        sinceLastStamp = if (lastStamp.isNaN()) sinceLastStamp + distance else distance - lastStamp
    }

    /** The terrain-local position of the world point [at], in a vector that the next call reuses. */
    fun toLocal(at: Vector3): Vector3 = local.set(at).mul(toLocal)

    /**
     * Visits each grid vertex within [radius] of the world point [at] as `visit(gx, gz, falloff)`, with the falloff
     * `1 - d / radius` from 0 (at the radius, not visited) to 1 (at the centre).
     */
    fun forEachInRadius(at: Vector3, radius: Float, visit: (Int, Int, Float) -> Unit) {
        val centre = toLocal(at)
        val fromX = max(0, floor((centre.x - radius / scaleX) / cell).toInt())
        val toX = min(resolution - 1, ceil((centre.x + radius / scaleX) / cell).toInt())
        val fromZ = max(0, floor((centre.z - radius / scaleZ) / cell).toInt())
        val toZ = min(resolution - 1, ceil((centre.z + radius / scaleZ) / cell).toInt())
        for (gz in fromZ..toZ) {
            for (gx in fromX..toX) {
                val dx = scaleX * (gx * cell - centre.x)
                val dz = scaleZ * (gz * cell - centre.z)
                val distance = sqrt(dx * dx + dz * dz)
                if (distance >= radius) continue
                visit(gx, gz, 1f - distance / radius)
            }
        }
    }
}
