/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.math.Vector3
import kotlin.math.*

private const val PRIMARY = 16
private const val LIGHT = 8
private const val KM = 1000.0
private const val GROUND_ALBEDO = 0.04

/** How far the cached sun may move (as 1 - cos of the angle) before [SkyAmbientEstimate.ambient] is computed again. */
private const val SUN_EPSILON = 1e-6

/**
 * The light of a procedural sky around clouds, linear RGB before exposure: [zenith] and [horizon] sky radiance (the
 * horizon averaged over four azimuths), and [sunlight], the sun's light after the atmosphere at [altitude] metres.
 */
class SkyAmbient(val zenith: FloatArray, val horizon: FloatArray, val sunlight: FloatArray, val altitude: Float)

/**
 * A CPU twin of the fixture sky shader's scattering (`skybox_physical/sky.frag`: the same sample counts and
 * constants) for [params], so cloud lighting matches the atmosphere without calling the asset's shader, and the physics
 * can be checked without a GL context. Results are linear radiance before exposure and tone mapping.
 *
 * [ambient] is cached for the last sun direction, so calling it every frame costs nothing while the sun stays put.
 * Not thread-safe: one per sky, on the thread that draws it.
 */
class SkyAmbientEstimate(private val params: AtmosphereParams, private val cameraHeight: Double = 100.0) {
    private var cachedSun: Vector3? = null
    private var cachedAltitude = Float.NaN
    private var cached: SkyAmbient? = null

    /** The sky around clouds at [altitude] metres with the sun toward [sun] (a unit vector); cached per sun direction. */
    fun ambient(sun: Vector3, altitude: Float = 1500f): SkyAmbient {
        val last = cachedSun
        cached?.let { if (last != null && altitude == cachedAltitude && 1.0 - last.dot(sun) < SUN_EPSILON) return it }
        val zenith = radiance(Vector3(0f, 1f, 0f), sun)
        val horizon = DoubleArray(3)
        for (k in 0 until 4) {
            val a = k * PI / 2
            val c = radiance(Vector3(sin(a).toFloat(), 0.05f, cos(a).toFloat()).nor(), sun)
            for (i in 0..2) horizon[i] += c[i] / 4
        }
        val result = SkyAmbient(zenith.toFloats(), horizon.toFloats(), sunlight(sun, altitude).toFloats(), altitude)
        cachedSun = Vector3(sun)
        cachedAltitude = altitude
        cached = result
        return result
    }

    /** The sun's light reaching [altitude] metres above the camera's ground point: its intensity times transmittance. */
    fun sunlight(sun: Vector3, altitude: Float): DoubleArray {
        val r = params.planetRadius / KM
        val ra = params.atmosphereRadius / KM
        val hR = params.heightRayleigh / KM
        val hM = params.heightMie / KM
        val s = doubleArrayOf(sun.x.toDouble(), sun.y.toDouble(), sun.z.toDouble())
        val o = doubleArrayOf(0.0, r + altitude / KM, 0.0)
        if (raySphere(o, s, r).let { it[0] > 0.0 && it[0] < it[1] }) return DoubleArray(3) // the sun is below the ground
        val step = raySphere(o, s, ra)[1] / LIGHT
        var depthR = 0.0
        var depthM = 0.0
        for (j in 0 until LIGHT) {
            val p = DoubleArray(3) { o[it] + s[it] * (step * (j + 0.5)) }
            val h = length(p) - r
            depthR += exp(-h / hR) * step
            depthM += exp(-h / hM) * step
        }
        return DoubleArray(3) { c ->
            params.sunIntensity * exp(-(params.betaRayleigh[c] * KM * depthR + 1.1 * params.betaMie * KM * depthM))
        }
    }

    /** Linear RGB radiance looking along [view] with the sun toward [sun] (both unit vectors, Y up). */
    fun radiance(view: Vector3, sun: Vector3): DoubleArray {
        val p = params
        val r = p.planetRadius / KM
        val ra = p.atmosphereRadius / KM
        val hR = p.heightRayleigh / KM
        val hM = p.heightMie / KM
        val betaR = DoubleArray(3) { p.betaRayleigh[it] * KM }
        val betaM = p.betaMie * KM
        val d = doubleArrayOf(view.x.toDouble(), view.y.toDouble(), view.z.toDouble())
        val s = doubleArrayOf(sun.x.toDouble(), sun.y.toDouble(), sun.z.toDouble())
        val o = doubleArrayOf(0.0, r + cameraHeight / KM, 0.0)

        val atmosphere = raySphere(o, d, ra)
        val planet = raySphere(o, d, r)
        val ground = planet[0] > 0.0 && planet[0] < planet[1]
        val tMin = max(atmosphere[0], 0.0)
        val tMax = if (ground) min(atmosphere[1], planet[0]) else atmosphere[1]
        val segment = (tMax - tMin) / PRIMARY

        val mu = dot(d, s)
        val g = p.mieG.toDouble()
        val phaseR = 3.0 / (16.0 * PI) * (1.0 + mu * mu)
        val phaseM =
            3.0 / (8.0 * PI) * ((1.0 - g * g) * (1.0 + mu * mu)) / ((2.0 + g * g) * (1.0 + g * g - 2.0 * g * mu).pow(1.5))

        val sumR = DoubleArray(3)
        val sumM = DoubleArray(3)
        var depthR = 0.0
        var depthM = 0.0
        for (i in 0 until PRIMARY) {
            val t = tMin + segment * (i + 0.5)
            val pos = DoubleArray(3) { o[it] + d[it] * t }
            val height = length(pos) - r
            val dR = exp(-height / hR) * segment
            val dM = exp(-height / hM) * segment
            depthR += dR
            depthM += dM

            val lightStep = raySphere(pos, s, ra)[1] / LIGHT
            var lightR = 0.0
            var lightM = 0.0
            var shadowed = false
            for (j in 0 until LIGHT) {
                val lp = DoubleArray(3) { pos[it] + s[it] * (lightStep * (j + 0.5)) }
                val lh = length(lp) - r
                if (lh < 0.0) {
                    shadowed = true
                    break
                }
                lightR += exp(-lh / hR) * lightStep
                lightM += exp(-lh / hM) * lightStep
            }
            if (!shadowed) {
                for (c in 0 until 3) {
                    val a = exp(-(betaR[c] * (depthR + lightR) + 1.1 * betaM * (depthM + lightM)))
                    sumR[c] += a * dR
                    sumM[c] += a * dM
                }
            }
        }

        val i0 = p.sunIntensity.toDouble()
        return DoubleArray(3) { c ->
            val transmittance = exp(-(betaR[c] * depthR + 1.1 * betaM * depthM))
            var v = i0 * (sumR[c] * betaR[c] * phaseR + sumM[c] * betaM * phaseM)
            if (ground) {
                val sunAtGround = exp(-(betaR[c] * hR + 1.1 * betaM * hM) / max(s[1], 0.02))
                v += transmittance * sunAtGround * (i0 * GROUND_ALBEDO * max(s[1], 0.0))
            }
            v
        }
    }
}

private fun DoubleArray.toFloats() = FloatArray(size) { this[it].toFloat() }

private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

private fun length(a: DoubleArray) = sqrt(dot(a, a))

private fun raySphere(o: DoubleArray, d: DoubleArray, radius: Double): DoubleArray {
    val b = dot(o, d)
    var h = b * b - (dot(o, o) - radius * radius)
    if (h < 0.0) return doubleArrayOf(1.0, -1.0)
    h = sqrt(h)
    return doubleArrayOf(-b - h, -b + h)
}
