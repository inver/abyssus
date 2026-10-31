/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.math.Vector3
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/** The least opacity a wire is drawn with, so a wire far under one pixel wide is still seen. */
const val WIRE_MIN_OPACITY = 0.12f

/** How sharp a sunlit wire's glint is. */
private const val GLINT_SHARPNESS = 24f

/** How wide a world distance of [distance] metres is on screen, in metres per pixel. */
fun pixelSize(distance: Float, fieldOfViewDegrees: Float, viewportHeight: Float): Float =
    2f * distance * tan(Math.toRadians(fieldOfViewDegrees / 2.0)).toFloat() / viewportHeight

/** How a wire [diameter] metres thick looks at [distance] metres: the width to draw (m) and its opacity. */
class WireLook(val width: Float, val opacity: Float)

/**
 * A wire of [diameter] metres at [distance] metres is drawn as wide as it is, or one pixel when that is less, with
 * opacity scaled by the share of that pixel it covers (never under [WIRE_MIN_OPACITY]).
 */
fun wireLook(diameter: Float, distance: Float, fieldOfViewDegrees: Float, viewportHeight: Float): WireLook {
    val pixel = pixelSize(distance, fieldOfViewDegrees, viewportHeight)
    return WireLook(max(diameter, pixel), min(1f, max(WIRE_MIN_OPACITY, diameter / pixel)))
}

/**
 * The glint of a thin cylinder along [tangent] (unit), lit from [toSun] and seen along [toViewer] (both unit): a
 * cylinder reflects into a cone, so it is brightest when the half vector is across the wire. 0 .. 1.
 */
fun wireGlint(tangent: Vector3, toSun: Vector3, toViewer: Vector3): Float {
    val hx = toSun.x + toViewer.x
    val hy = toSun.y + toViewer.y
    val hz = toSun.z + toViewer.z
    val length = sqrt(hx * hx + hy * hy + hz * hz)
    if (length < 1e-4f) return 0f
    val along = (tangent.x * hx + tangent.y * hy + tangent.z * hz) / length
    return max(0f, 1f - along * along).pow(GLINT_SHARPNESS / 2f)
}
