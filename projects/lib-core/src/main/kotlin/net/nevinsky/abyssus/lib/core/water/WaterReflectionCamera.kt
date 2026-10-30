/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3

/** A camera's position and orientation: [direction] is where it looks and [up] its up vector, both unit length. */
class ViewPose(val position: Vector3, val direction: Vector3, val up: Vector3)

/**
 * The mirror image of a camera across the horizontal plane `y = level`, for a planar reflection. Pure math (libGDX
 * vectors only, no GL).
 *
 * The reflection is rendered by drawing the real scene through `view * reflection` (see [matrix]): each point `P`
 * lands where its mirror image `P'` would be seen from the real camera, so a water fragment at world position `W` on the
 * plane projects to the same pixel in the reflected image as in the main one, and the image can be sampled at the
 * fragment's own screen position. Because the mirror has determinant -1, triangles drawn through it have the opposite
 * winding ([flipsWinding]): the reflected pass draws with clockwise front faces.
 */
class WaterReflectionCamera {
    /** True: a reflected pass swaps the front-face winding (the mirror has determinant -1). */
    val flipsWinding: Boolean = true

    /** The point [p] mirrored across the plane `y = level`. */
    fun point(p: Vector3, level: Float): Vector3 = Vector3(p.x, 2f * level - p.y, p.z)

    /** The direction [d] mirrored (a vector, so the plane's offset does not apply). */
    fun vector(d: Vector3): Vector3 = Vector3(d.x, -d.y, d.z)

    /** The camera's position, direction and up vector mirrored across `y = level`. */
    fun pose(position: Vector3, direction: Vector3, up: Vector3, level: Float): ViewPose =
        ViewPose(point(position, level), vector(direction), vector(up))

    /** The matrix taking each world point to its mirror image across `y = level`. */
    fun matrix(level: Float): Matrix4 = Matrix4().also {
        it.setToScaling(1f, -1f, 1f)
        it.setTranslation(0f, 2f * level, 0f)
    }

    /**
     * The clip plane that keeps what is on the water's side of the surface: points with
     * `dot(normal, p) + offset >= 0` stay. [bias] lowers the plane a little so the shoreline has no gap where a
     * clipped surface meets the water.
     */
    fun clipPlane(level: Float, bias: Float): FloatArray = floatArrayOf(0f, 1f, 0f, -(level - bias))

    /**
     * How far below the level geometry is still kept: a little more for taller waves and for a camera farther away
     * (a reflected surface seen at grazing angles shows more of the shore's skirt).
     */
    fun bias(waveAmplitude: Double, distanceToSurface: Float): Float =
        (0.02f + waveAmplitude.toFloat() * 1.5f + distanceToSurface * 0.0005f).coerceIn(0.02f, 2f)
}
