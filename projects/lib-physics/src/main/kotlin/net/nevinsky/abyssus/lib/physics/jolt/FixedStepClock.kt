/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt

import net.nevinsky.abyssus.lib.physics.MAX_STEPS_PER_ADVANCE
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Carries fractional time; drops whole steps beyond the per-advance limit. */
internal class FixedStepClock {
    private var accumulated = 0.0

    fun advance(seconds: Float): Int {
        require(seconds.isFinite() && seconds >= 0f) { "cannot advance by $seconds s" }
        accumulated += seconds
        val steps = min(floor(accumulated / PHYSICS_STEP + 1e-6).toInt(), MAX_STEPS_PER_ADVANCE)
        accumulated = max(0.0, accumulated - steps * PHYSICS_STEP.toDouble())
        if (accumulated >= PHYSICS_STEP) accumulated %= PHYSICS_STEP.toDouble()
        return steps
    }
}
