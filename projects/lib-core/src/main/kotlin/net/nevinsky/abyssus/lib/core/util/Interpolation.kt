/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.util

/** The cubic ease `3t² - 2t³`: 0 at 0, 1 at 1, flat at both ends. [t] is not clamped. */
fun smooth01(t: Float): Float = t * t * (3f - 2f * t)

/** GLSL `smoothstep`: 0 up to [e0], 1 from [e1], [smooth01] between. */
fun smoothstep(e0: Float, e1: Float, x: Float): Float = smooth01(((x - e0) / (e1 - e0)).coerceIn(0f, 1f))

/** Linear interpolation from [a] to [b] by [t]. */
fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
