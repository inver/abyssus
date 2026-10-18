/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.util

import com.badlogic.gdx.math.MathUtils
import kotlin.math.atan2

/** [degrees] wrapped into (-180, 180], retaining the caller's precision. */
fun wrapDegrees(degrees: Float): Float {
    var d = degrees % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    return d
}

fun wrapDegrees(degrees: Double): Double {
    var d = degrees % 360.0
    if (d > 180.0) d -= 360.0
    if (d <= -180.0) d += 360.0
    return d
}

/** Azimuth around +Y, from +X toward -Z. Signed zero follows atan2 at the negative X axis. */
fun azimuthRadiansOf(x: Float, z: Float): Float = atan2(-z, x)
fun azimuthRadiansOf(x: Double, z: Double): Double = atan2(-z, x)

fun azimuthOf(x: Float, z: Float): Float = azimuthRadiansOf(x, z) * MathUtils.radiansToDegrees
fun azimuthOf(x: Double, z: Double): Double = Math.toDegrees(azimuthRadiansOf(x, z))
