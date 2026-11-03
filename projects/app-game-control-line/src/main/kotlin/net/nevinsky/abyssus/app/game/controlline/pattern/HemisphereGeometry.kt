/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import kotlin.math.acos
import kotlin.math.asin
import net.nevinsky.abyssus.lib.core.util.azimuthOf
import kotlin.math.cos
import kotlin.math.sin

/** The F2B flight hemisphere's base is this high above the pilot's ground point (m). */
const val BASE_HEIGHT = 1.5f

/** The F2B parallels, in degrees of latitude above the base. */
const val PARALLEL_45 = 45f

/**
 * The flight hemisphere (design decision 1): centred [BASE_HEIGHT] above the pilot's ground point, of radius [radius]
 * (the line length). A place on it is a unit vector `u` from the centre; latitude is measured up from the base and the
 * azimuth grows in the flying direction (anticlockwise seen from above), as [TrackSample.azimuth] does. Nothing here is
 * singular at the zenith.
 */
class HemisphereGeometry(val radius: Float) {
    /** The direction from the centre to the plane of [sample] (unit; a new vector). */
    fun direction(sample: TrackSample): Vector3 = Vector3(sample.offset).sub(0f, BASE_HEIGHT, 0f).nor()

    /** The unit vector at [latitude] and [azimuth] degrees (a new vector). */
    fun direction(latitude: Float, azimuth: Float): Vector3 {
        val lat = latitude * MathUtils.degreesToRadians
        val az = azimuth * MathUtils.degreesToRadians
        return Vector3(cos(lat) * cos(az), sin(lat), -cos(lat) * sin(az))
    }

    /** The latitude of [u], degrees above the base, -90..90. */
    fun latitude(u: Vector3): Float = asin(u.y.coerceIn(-1f, 1f)) * MathUtils.radiansToDegrees

    /** The azimuth of [u] in degrees, (-180, 180]; meaningless at the poles. */
    fun azimuth(u: Vector3): Float = azimuthOf(u.x, u.z)

    /**
     * Where the great circle through [a] and [b] crosses the base, as an azimuth (degrees) on the side of [a], or null
     * when the two places are (anti)parallel or the circle is the base itself.
     */
    fun equatorCrossing(a: Vector3, b: Vector3): Float? {
        val normal = Vector3(a).crs(b)
        if (normal.len2() < 1e-8f) return null
        val d = normal.crs(Vector3(0f, 1f, 0f))
        if (d.len2() < 1e-8f) return null
        if (d.dot(a) < 0f) d.scl(-1f)
        return azimuth(d.nor())
    }

    /** The azimuth of [u] in laps, (-0.5, 0.5]. */
    fun azimuthLaps(u: Vector3): Float = azimuth(u) / 360f

    /** The world position of [u], relative to the pilot's ground point (a new vector). */
    fun position(u: Vector3): Vector3 = Vector3(u).scl(radius).add(0f, BASE_HEIGHT, 0f)

    /** The angle between [a] and [b] in degrees. */
    fun angle(a: Vector3, b: Vector3): Float = acos(a.dot(b).coerceIn(-1f, 1f)) * MathUtils.radiansToDegrees

    /** The length (m) of the great-circle arc of [degrees] on the hemisphere. */
    fun arc(degrees: Float): Float = degrees * MathUtils.degreesToRadians * radius

    /** The length (m) of a turn of [laps] along the base. */
    fun lapsToMetres(laps: Float): Float = arc(laps * 360f)

    /** The straight distance (m) between the places [a] and [b]. */
    fun distance(a: Vector3, b: Vector3): Float = Vector3(a).sub(b).len() * radius

    /** The point [t] (0..1) of the way along the great circle from [a] to [b] (a new unit vector). */
    fun greatCircle(a: Vector3, b: Vector3, t: Float): Vector3 {
        val omega = acos(a.dot(b).coerceIn(-1f, 1f))
        if (omega < 1e-5f) return Vector3(a)
        val sinOmega = sin(omega)
        return Vector3(a).scl(sin((1f - t) * omega) / sinOmega).mulAdd(b, sin(t * omega) / sinOmega).nor()
    }

    /**
     * The point at [phase] degrees on the small circle of angular radius [angularRadius] degrees about [center] (unit), a
     * new unit vector. The circle passes through [from] at phase 0 and its phase grows with the azimuth at its lowest
     * point, the way an inside loop is flown.
     */
    fun smallCircle(center: Vector3, angularRadius: Float, phase: Float, from: Vector3): Vector3 {
        val rho = angularRadius * MathUtils.degreesToRadians
        val p = phase * MathUtils.degreesToRadians
        // a: unit vector from centre towards `from`, perpendicular to centre; b = center x a
        val a = Vector3(from).mulAdd(center, -from.dot(center)).nor()
        val b = Vector3(center).crs(a)
        val ring = Vector3(a).scl(cos(p)).mulAdd(b, sin(p))
        return Vector3(center).scl(cos(rho)).mulAdd(ring, sin(rho)).nor()
    }

    /** The point on the small circle about [center] of angular radius [angularRadius] degrees at [theta] degrees round it, [theta] 0 being its bottom (lowest latitude). */
    fun smallCircleFromBottom(center: Vector3, angularRadius: Float, theta: Float): Vector3 {
        val down = Vector3(0f, -1f, 0f)
        val axis = Vector3(down).mulAdd(center, -down.dot(center))
        val fromBottom = if (axis.len2() < 1e-8f) Vector3(1f, 0f, 0f) else axis.nor()
        val rho = angularRadius * MathUtils.degreesToRadians
        val start = Vector3(center).scl(cos(rho)).mulAdd(fromBottom, sin(rho)).nor()
        return smallCircle(center, angularRadius, theta, start)
    }

    /** The distance of [u] from the great circle through [a] and [b], in degrees (unsigned). */
    fun offGreatCircle(u: Vector3, a: Vector3, b: Vector3): Float {
        val normal = Vector3(a).crs(b)
        if (normal.len2() < 1e-10f) return angle(u, a)
        normal.nor()
        return asin(kotlin.math.abs(u.dot(normal)).coerceIn(0f, 1f)) * MathUtils.radiansToDegrees
    }
}
