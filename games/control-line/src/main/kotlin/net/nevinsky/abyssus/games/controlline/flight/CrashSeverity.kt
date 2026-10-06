/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.flight

/** Coming down at this or faster (m/s) a crash breaks the wing and the tail ([CrashSeverity.MEDIUM]). */
const val MEDIUM_CRASH_SPEED = 6f

/** Coming down at this or faster (m/s) a crash wrecks the plane ([CrashSeverity.FULL]). */
const val FULL_CRASH_SPEED = 10f

/** How badly a plane broke when it crashed into the ground, with the model's clip that shows it. */
enum class CrashSeverity(val clip: String) {
    /** The landing gear breaks. */
    LITTLE("crash_little"),

    /** The wing and the tail break. */
    MEDIUM("crash_medium"),

    /** Gear, propeller, wings and tail come off. */
    FULL("crash_full"),
}

/**
 * The severity of a crash from its impact speed: how fast the plane came down onto the ground (m/s), the speed that
 * made it a crash ([CRASH_SPEED]). Below [MEDIUM_CRASH_SPEED] the gear breaks, below [FULL_CRASH_SPEED] the wing and
 * the tail, from it everything. An upside-down touch slower than [CRASH_SPEED] is a [CrashSeverity.LITTLE] one.
 */
fun crashSeverity(impactSpeed: Float): CrashSeverity = when {
    impactSpeed >= FULL_CRASH_SPEED -> CrashSeverity.FULL
    impactSpeed >= MEDIUM_CRASH_SPEED -> CrashSeverity.MEDIUM
    else -> CrashSeverity.LITTLE
}
