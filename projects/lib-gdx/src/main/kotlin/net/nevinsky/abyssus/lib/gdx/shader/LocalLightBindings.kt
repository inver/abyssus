/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.utils.Array as GdxArray

/*
 * Point and spot lights as rows of a float texture, read by `default.fragment.glsl` and `pbr.fragment.glsl`: 256 of each
 * as uniform arrays need far more than the fragment constant registers a program may use.
 * Row i (a point light) holds texel 0: color (rgb) and range (a), texel 1: position. Row [LOCAL_LIGHT_SPOT_ROW] + i
 * (a spot light) holds texel 0: color and range, texel 1: position and the half cone angle in degrees, texel 2:
 * direction and the inward edge softness. The shader repeats these numbers; LocalLightLayoutTest keeps them in step.
 */
const val LOCAL_LIGHT_WIDTH = 4
const val LOCAL_LIGHT_MAX = 256
const val LOCAL_LIGHT_SPOT_ROW = LOCAL_LIGHT_MAX
const val LOCAL_LIGHT_HEIGHT = LOCAL_LIGHT_MAX * 2

/**
 * The local lights of an environment packed for the shader and uploaded to one [FloatDataTexture] when they changed.
 * One instance belongs to one shader; it allocates nothing once warm.
 */
class LocalLightBindings {
    private val data = FloatArray(LOCAL_LIGHT_WIDTH * LOCAL_LIGHT_HEIGHT * 4)
    private var texture: FloatDataTexture? = null
    private var pointCount = 0
    private var spotCount = 0
    private var dirty = true

    /** The texture holding the packed lights, made on first use; the caller binds it to a unit and passes that to [upload]. */
    val localLightTexture: FloatDataTexture
        get() = texture ?: FloatDataTexture(LOCAL_LIGHT_WIDTH, LOCAL_LIGHT_HEIGHT).also { texture = it }

    /** How many point lights [pack] kept (at most [LOCAL_LIGHT_MAX]). */
    val points: Int get() = pointCount

    /** How many spot lights [pack] kept (at most [LOCAL_LIGHT_MAX]). */
    val spots: Int get() = spotCount

    /** Packs [points] and [spots]; the texture needs [upload] only when this changed what it holds. */
    fun pack(points: GdxArray<PointLight>?, spots: GdxArray<SpotLight>?) {
        val nPoints = minOf(points?.size ?: 0, LOCAL_LIGHT_MAX)
        val nSpots = minOf(spots?.size ?: 0, LOCAL_LIGHT_MAX)
        if (nPoints != pointCount || nSpots != spotCount) {
            pointCount = nPoints
            spotCount = nSpots
            dirty = true
        }
        for (i in 0 until nPoints) {
            val light = points!![i]
            val at = texel(i, 0)
            set(at, light.color.r, light.color.g, light.color.b, light.intensity)
            set(at + 4, light.position.x, light.position.y, light.position.z, 0f)
        }
        for (i in 0 until nSpots) {
            val light = spots!![i]
            val at = texel(LOCAL_LIGHT_SPOT_ROW + i, 0)
            set(at, light.color.r, light.color.g, light.color.b, light.intensity)
            set(at + 4, light.position.x, light.position.y, light.position.z, light.cutoffAngle)
            set(at + 8, light.direction.x, light.direction.y, light.direction.z, light.exponent)
        }
    }

    /** Writes the lights to the texture on [unit] if they changed (or the context was lost). */
    fun upload(unit: Int) {
        val texture = localLightTexture
        if (dirty || !texture.allocated) {
            texture.upload(unit, data)
            dirty = false
        }
    }

    fun dispose() {
        texture?.dispose()
        texture = null
        dirty = true
    }

    /** Stores four floats, noting a change. */
    private fun set(at: Int, a: Float, b: Float, c: Float, d: Float) {
        if (data[at] != a || data[at + 1] != b || data[at + 2] != c || data[at + 3] != d) {
            data[at] = a
            data[at + 1] = b
            data[at + 2] = c
            data[at + 3] = d
            dirty = true
        }
    }

    private fun texel(row: Int, column: Int) = (row * LOCAL_LIGHT_WIDTH + column) * 4
}
