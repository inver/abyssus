/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** The least share of the sun's light that passes the thickest clouds. */
const val SUN_OCCLUSION_FLOOR = 0.1f

/** The time constant, in seconds, of the smoothing that keeps the sun from flickering as clouds drift. */
const val SUN_OCCLUSION_SMOOTHING = 0.5f

/**
 * How much of the sun's light the clouds let through toward a point on the ground: the transmittance along the sun
 * direction through every band ([CloudField.column] density where the sun ray crosses mid-band, times the band's
 * density and the ray's path through it), never below [SUN_OCCLUSION_FLOOR], smoothed over time. Pure, no GL; one per
 * view, updated once per frame.
 */
class SunOcclusion(private val field: CloudField = CloudField()) {
    /** The smoothed transmittance of the last [update]; 1 when nothing covers the sun. */
    var transmittance = 1f
        private set

    private var started = false

    /**
     * Advances by [deltaSeconds] toward the transmittance of [clouds] (null or invisible: none) for the sun toward [sun]
     * seen from ([targetX], [targetZ]) at [timeSeconds]; [planetRadius] and [cameraHeight] place the bands as the sky
     * draws them. The first update after [reset] (or after clouds appear) jumps straight to it.
     */
    fun update(
        clouds: CloudSettings?,
        sun: Vector3,
        targetX: Float,
        targetZ: Float,
        planetRadius: Float,
        cameraHeight: Float,
        timeSeconds: Double,
        deltaSeconds: Float,
    ): Float {
        if (clouds == null || !clouds.visible) {
            reset()
            return transmittance
        }
        val goal = instant(clouds, sun, targetX, targetZ, planetRadius, cameraHeight, timeSeconds)
        if (!started) {
            transmittance = goal
            started = true
        } else if (deltaSeconds > 0f) {
            transmittance += (goal - transmittance) * (1f - exp(-deltaSeconds / SUN_OCCLUSION_SMOOTHING))
        }
        return transmittance
    }

    /** The unsmoothed transmittance (see [update]). */
    fun instant(
        clouds: CloudSettings,
        sun: Vector3,
        targetX: Float,
        targetZ: Float,
        planetRadius: Float,
        cameraHeight: Float,
        timeSeconds: Double,
    ): Float {
        if (!clouds.visible) return 1f
        var depth = 0f
        for (band in clouds.bands.values) {
            val mid = (band.base + band.top) / 2f
            val t = bandDistance(sun, mid, planetRadius, cameraHeight) ?: continue
            val coverage = field.coverage(band, targetX + sun.x * t, targetZ + sun.z * t, field.windOffset(band, timeSeconds))
            val path = band.thickness / max(abs(sun.y), 0.1f)
            depth += CLOUD_EXTINCTION * band.density * field.column(band.type, coverage) * path
        }
        return max(exp(-depth), SUN_OCCLUSION_FLOOR)
    }

    /** Forgets the smoothed value: the next [update] starts from its target. */
    fun reset() {
        transmittance = 1f
        started = false
    }
}

/**
 * The distance along the unit [direction] from a viewer [cameraHeight] metres above a planet of [planetRadius] to the
 * sphere [altitude] metres above the ground (the way out when the viewer is below it); null when the ray misses it.
 */
fun bandDistance(direction: Vector3, altitude: Float, planetRadius: Float, cameraHeight: Float): Float? {
    // relative to the planet's centre, in doubles: a planet radius in metres leaves a float too few digits
    val oy = planetRadius.toDouble() + cameraHeight
    val r = planetRadius.toDouble() + altitude
    val b = oy * direction.y
    val c = oy * oy - r * r
    val h = b * b - c
    if (h < 0.0) return null
    val t = -b + sqrt(h)
    return if (t > 0.0) t.toFloat() else null
}
