/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import org.junit.Assert.assertEquals
import org.junit.Test

class WireTest {
    private val wire = Wire(0.0005f)

    @Test
    fun halfAMillimetreSteelWeighsAboutOnePointFiveGramsAMetre() {
        assertEquals(0.00154f, wire.massPerMetre, 0.00002f)
    }

    @Test
    fun fifteenMetresAreAsStiffAsTwoPointSixKilonewtonsAMetre() {
        assertEquals(2618f, wire.stiffness(15f), 20f)
    }

    @Test
    fun thirtyNewtonsStretchFifteenMetresByAboutElevenMillimetres() {
        assertEquals(0.0115f, wire.stretch(15f, 30f), 0.0005f)
    }

    @Test
    fun aFifteenMetreWireAddsAboutFifteenGramsToThePlane() {
        assertEquals(0.0077f, wire.tipMass(15f), 0.0003f)
    }
}
