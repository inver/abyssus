/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview.skybox

import net.nevinsky.abyssus.sceneview.Vec3
import net.nevinsky.abyssus.sceneview.skybox.procedural.AtmosphereParams
import kotlin.math.*

/**
 * A CPU twin of the sky fragment shader's scattering (the same sample counts and constants), so the physics can be
 * checked without a GL context. The result is linear radiance before exposure and tone mapping.
 */
object AtmosphereModel {
    const val PRIMARY = 16
    const val LIGHT = 8
    const val KM = 1000.0
    const val GROUND_ALBEDO = 0.04

    /** Linear RGB radiance looking along [view] with the sun toward [sun] (both unit vectors, Y up). */
    fun radiance(view: Vec3, sun: Vec3, p: AtmosphereParams, cameraHeight: Double = 100.0): DoubleArray {
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

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun length(a: DoubleArray) = sqrt(dot(a, a))

    private fun raySphere(o: DoubleArray, d: DoubleArray, radius: Double): DoubleArray {
        val b = dot(o, d)
        var h = b * b - (dot(o, o) - radius * radius)
        if (h < 0.0) return doubleArrayOf(1.0, -1.0)
        h = sqrt(h)
        return doubleArrayOf(-b - h, -b + h)
    }
}
