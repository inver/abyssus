/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3

/**
 * Depth for the water passes: a linear view depth (the distance along the camera's forward axis, as a share of the
 * far plane) packed into the four 8-bit channels of a colour target, and the reconstruction of a world position from
 * it. The packing is the one the shaders use (`packDepth` in the model depth shader). A pixel nothing drew has the
 * reserved value [EMPTY] (1: the far plane), which means deep water, not a shoreline. Pure; no GL.
 */
class WaterDepthMath {
    /** The four 8-bit channels (0 to 255) holding [depth], a value from 0 to 1, as a colour target stores them. */
    fun pack(depth: Float): IntArray {
        val d = depth.toDouble().coerceIn(0.0, MAX_PACKED)
        val scale = doubleArrayOf(16581375.0, 65025.0, 255.0, 1.0)
        val value = DoubleArray(4) { frac(d * scale[it]) }
        val shifted = doubleArrayOf(0.0, value[0], value[1], value[2])
        for (i in 1..3) value[i] -= shifted[i] / 255.0
        return IntArray(4) { (value[it] * 255.0 + 0.5).toInt().coerceIn(0, 255) }
    }

    /** The depth four 8-bit channels (0 to 255) hold; the inverse of [pack] to its precision. */
    fun unpack(channels: IntArray): Float {
        require(channels.size == 4) { "four channels" }
        val c = DoubleArray(4) { channels[it] / 255.0 }
        return (c[0] / 16581375.0 + c[1] / 65025.0 + c[2] / 255.0 + c[3]).toFloat()
    }

    /** The share of the far plane a view depth of [distance] is. */
    fun normalized(distance: Float, far: Float): Float = (distance / far).coerceIn(0f, 1f)

    /** Whether packed [depth] says nothing was drawn there. */
    fun isEmpty(depth: Float): Boolean = depth >= EMPTY - EMPTY_EPSILON

    /**
     * The world position of the pixel at normalised device coordinates ([ndcX], [ndcY]) whose view depth is
     * [viewDepth] (world units along [forward]), for a camera at [eye] looking along the unit [forward] with
     * [inverseViewProjection] the inverse of its projection-view matrix. Null when the pixel's ray runs (nearly) at right
     * angles to [forward], where a view depth fixes no point.
     */
    fun worldPosition(
        inverseViewProjection: Matrix4, eye: Vector3, forward: Vector3, ndcX: Float, ndcY: Float, viewDepth: Float,
    ): Vector3? {
        val near = Vector3(ndcX, ndcY, -1f).prj(inverseViewProjection)
        val far = Vector3(ndcX, ndcY, 1f).prj(inverseViewProjection)
        val ray = far.sub(near).nor()
        val along = ray.dot(forward)
        if (!along.isFinite() || along < MIN_ALONG) return null
        return Vector3(eye).mulAdd(ray, viewDepth / along)
    }

    /**
     * Whether a bottom at [bottomY] is a legitimate submerged sample under a surface at [surfaceY] with waves up to
     * [amplitude] high: a sample reconstructed above the surface is dry land (or an object) and must not be pulled into
     * the refraction.
     */
    fun isSubmerged(bottomY: Float, surfaceY: Float, amplitude: Float): Boolean = bottomY < surfaceY + amplitude * 0.25f

    private fun frac(v: Double) = v - Math.floor(v)
}

/** Just under 1, so a packed depth never rounds into the empty value. */
private const val MAX_PACKED = 0.99999994

/** The reserved depth of a pixel nothing drew. */
private const val EMPTY = 1f
private const val EMPTY_EPSILON = 1e-6f
private const val MIN_ALONG = 1e-4f
