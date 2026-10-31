/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.util

import kotlin.math.exp

/**
 * The multiplier for a `dot(d, d) * k` fog term that reaches the exponential fog share `1 - exp(-1)` at `1 / density`,
 * whatever the fog's gradient. The editor's model and terrain shaders fog with such a quadratic ramp.
 */
fun quadraticFogCoefficient(density: Float): Float = ((1.0 - exp(-1.0)) * density.toDouble() * density).toFloat()
