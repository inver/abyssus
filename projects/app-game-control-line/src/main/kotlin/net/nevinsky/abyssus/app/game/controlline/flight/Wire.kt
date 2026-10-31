/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import kotlin.math.PI

/** Density of steel (kg/m³). */
const val STEEL_DENSITY = 7850f

/** Young's modulus of steel (Pa). */
const val STEEL_MODULUS = 200e9f

/** A control line: a steel wire of [diameter] metres. */
class Wire(val diameter: Float) {
    /** Cross-section (m²). */
    val area: Float = (PI * diameter * diameter / 4.0).toFloat()

    /** Mass per metre (kg/m). */
    val massPerMetre: Float = STEEL_DENSITY * area

    /** Weight per metre (N/m). */
    val weightPerMetre: Float = massPerMetre * net.nevinsky.abyssus.lib.physics.GRAVITY

    /** Stiffness of [length] metres of wire (N/m): `E * A / L`. */
    fun stiffness(length: Float): Float = STEEL_MODULUS * area / length

    /** How much longer [length] metres of wire is under [tension] newtons (m). */
    fun stretch(length: Float, tension: Float): Float = tension / stiffness(length)

    /** The mass a wire of [length] adds to the plane it swings: one third of its own, as a rod swung about its end. */
    fun tipMass(length: Float): Float = massPerMetre * length / 3f
}
