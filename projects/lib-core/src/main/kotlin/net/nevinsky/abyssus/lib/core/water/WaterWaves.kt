/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One travelling sine wave: it moves along ([dirX], [dirZ]); [k] is its wave number, [omega] its angular frequency. */
class WaterWave(val dirX: Double, val dirZ: Double, val k: Double, val omega: Double, val amplitude: Double, val offset: Double)

/**
 * The surface height of a water surface: a small, fixed sum of [WAVES] travelling sines (vertical displacement only, no
 * fluid simulation) with analytic slopes, so the normal needs no neighbouring samples. The phase depends on world
 * position and time alone, never on the camera, so the water keeps its shape while the view moves. The largest
 * height is [amplitude] (the shares of the waves sum to 1) and with an [amplitude] of 0 the surface is flat.
 *
 * The shader evaluates the same sum from [uniform] (with [phases] reduced to one turn on the CPU, where double
 * precision is available, so a long-running view does not lose the animation to float error).
 */
class WaterWaves(amplitude: Double, wavelength: Double, speed: Double) {
    val amplitude: Double = if (amplitude.isFinite()) amplitude.coerceIn(0.0, MAX_VALUE) else 0.0
    private val base: Double = if (wavelength.isFinite() && wavelength > 0.0) wavelength.coerceIn(MIN_WAVELENGTH, MAX_VALUE) else 1.0
    private val speed: Double = if (speed.isFinite()) speed.coerceIn(0.0, MAX_VALUE) else 0.0

    val waves: List<WaterWave> = (0 until WAVES).map { i ->
        val length = base * LENGTH_SCALE[i]
        val k = 2.0 * PI / length
        // shorter waves travel slower, like deep-water waves
        val omega = k * this.speed * sqrt(LENGTH_SCALE[i])
        WaterWave(cos(ANGLE[i]), sin(ANGLE[i]), k, omega, this.amplitude * SHARE[i], OFFSET[i])
    }

    /** The height above the mean level at world position ([x], [z]) and time [seconds]. */
    fun height(x: Double, z: Double, seconds: Double): Double {
        var h = 0.0
        for (w in waves) h += w.amplitude * sin(phase(w, x, z, seconds))
        return h
    }

    /** The slopes (dh/dx, dh/dz) of the surface at ([x], [z]) and time [seconds]. */
    fun slope(x: Double, z: Double, seconds: Double): Pair<Double, Double> {
        var sx = 0.0
        var sz = 0.0
        for (w in waves) {
            val c = w.amplitude * w.k * cos(phase(w, x, z, seconds))
            sx += c * w.dirX
            sz += c * w.dirZ
        }
        return sx to sz
    }

    /** The unit surface normal at ([x], [z]) and time [seconds]; always finite. */
    fun normal(x: Double, z: Double, seconds: Double): Vector3 {
        val (sx, sz) = slope(x, z, seconds)
        val length = sqrt(sx * sx + 1.0 + sz * sz)
        return Vector3((-sx / length).toFloat(), (1.0 / length).toFloat(), (-sz / length).toFloat())
    }

    /** Each wave's phase offset at [seconds], reduced to [0, 2 pi): what the shader adds to `k * dot(direction, position)`. */
    fun phases(seconds: Double): FloatArray = FloatArray(WAVES) {
        val w = waves[it]
        val turn = 2.0 * PI
        val p = (w.offset - w.omega * seconds) % turn
        (if (p < 0.0) p + turn else p).toFloat()
    }

    /** The wave table the shader reads: per wave `(directionX, directionZ, k, amplitude)`. */
    fun uniform(): FloatArray = FloatArray(WAVES * 4) {
        val w = waves[it / 4]
        when (it % 4) {
            0 -> w.dirX.toFloat()
            1 -> w.dirZ.toFloat()
            2 -> w.k.toFloat()
            else -> w.amplitude.toFloat()
        }
    }

    private fun phase(w: WaterWave, x: Double, z: Double, seconds: Double): Double =
        w.k * (w.dirX * x + w.dirZ * z) - w.omega * seconds + w.offset
}

/** How many waves the sum has; the shader loops over the same number. */
const val WAVES = 4

private const val MAX_VALUE = 1.0e6
private const val MIN_WAVELENGTH = 0.01

/** Each wave's length as a share of the surface's wavelength. */
private val LENGTH_SCALE = doubleArrayOf(1.0, 0.63, 0.41, 0.27)

/** Each wave's share of the amplitude; they add up to 1, so the surface never rises above the amplitude. */
private val SHARE = doubleArrayOf(0.5, 0.28, 0.14, 0.08)

/** Each wave's heading in radians. */
private val ANGLE = doubleArrayOf(0.0, 0.65, -1.07, 1.78)

private val OFFSET = doubleArrayOf(0.0, 1.3, 2.1, 0.7)

/** The steepest slope of a wave sum, a bound for the shader's normal strength. */
fun WaterWaves.maxSlope(): Double = waves.sumOf { abs(it.amplitude * it.k) }
