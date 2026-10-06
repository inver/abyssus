/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.scene

import net.nevinsky.abyssus.editor.content.Vec3

import kotlin.math.cos
import kotlin.math.sqrt

/** Full cone width in degrees; softness occupies an inward fraction of the angular radius. */
data class SpotCone(val angle: Float, val softness: Float) {
    init {
        require(angle.isFinite() && angle > 0f && angle < 180f)
        require(softness.isFinite() && softness in 0f..1f)
    }
    val outerCos = cos(Math.toRadians(angle.toDouble() / 2)).toFloat()
    val innerCos = cos(Math.toRadians(angle.toDouble() / 2 * (1 - softness))).toFloat()

    fun attenuation(axis: Vec3, ray: Vec3): Float {
        val a2 = axis.x * axis.x + axis.y * axis.y + axis.z * axis.z
        val r2 = ray.x * ray.x + ray.y * ray.y + ray.z * ray.z
        if (!a2.isFinite() || !r2.isFinite() || a2 <= 0f || r2 <= 0f) return 0f
        val cosine = (axis.x * ray.x + axis.y * ray.y + axis.z * ray.z) / sqrt(a2 * r2)
        if (softness == 0f || innerCos == outerCos) return if (cosine >= outerCos) 1f else 0f
        return smooth((cosine - outerCos) / (innerCos - outerCos))
    }

    fun rangeAttenuation(distance: Float, range: Float): Float {
        if (!distance.isFinite() || !range.isFinite() || distance < 0f || range <= 0f) return 0f
        return 1f - smooth((distance - 0.75f * range) / (0.25f * range))
    }

    private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
