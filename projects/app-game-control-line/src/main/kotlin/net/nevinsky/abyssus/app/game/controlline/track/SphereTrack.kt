/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.track

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where a plane is on the sphere of its lines, at [time] seconds into the flight. Angles are in degrees:
 * - [azimuth] around the pilot, growing in the flying direction (anticlockwise seen from above), in (-180, 180];
 * - [elevation] above the handle's horizon, -90..90;
 * - [climb], the velocity's angle within the sphere's tangent plane: 0 along the circle in the flying direction,
 *   90 straight up, ±180 along the circle the other way, in (-180, 180];
 * - [inverted]: the plane's up points away from the sphere's local up.
 */
data class TrackSample(
    val time: Float,
    val azimuth: Float,
    val elevation: Float,
    val climb: Float,
    val inverted: Boolean,
    val airborne: Boolean,
)

/** [degrees] wrapped into (-180, 180]. */
fun wrapDegrees(degrees: Float): Float {
    var d = degrees % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    return d
}

/**
 * Turns a plane's pose and velocity, relative to the handle, into a [TrackSample]. The sphere's frame at the plane: `n`
 * points out from the handle, `e` along the circle in the flying direction and `k = n x e` up along the sphere.
 */
class SphereTrack {
    private val relative = Vector3()
    private val out = Vector3()
    private val east = Vector3()
    private val up = Vector3()
    private val planeUp = Vector3()

    fun sample(time: Float, handle: Vector3, position: Vector3, velocity: Vector3, rotation: Quaternion, airborne: Boolean): TrackSample {
        relative.set(position).sub(handle)
        val radius = relative.len()
        val azimuth = atan2(-relative.z, relative.x)
        val elevation = if (radius > 0f) asin((relative.y / radius).coerceIn(-1f, 1f)) else 0f
        out.set(relative).scl(if (radius > 0f) 1f / radius else 0f)
        east.set(-sin(azimuth), 0f, -cos(azimuth))
        up.set(out).crs(east)
        val climb = atan2(velocity.dot(up), velocity.dot(east))
        rotation.transform(planeUp.set(0f, 1f, 0f))
        return TrackSample(
            time,
            wrapDegrees(azimuth * MathUtils.radiansToDegrees),
            elevation * MathUtils.radiansToDegrees,
            wrapDegrees(climb * MathUtils.radiansToDegrees),
            planeUp.dot(up) < 0f,
            airborne,
        )
    }
}
