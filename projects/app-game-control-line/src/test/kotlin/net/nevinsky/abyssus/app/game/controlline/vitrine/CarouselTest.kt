/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.vitrine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselTest {
    @Test
    fun theSelectedPlaneStandsInTheCentreAndTheOthersBesideIt() {
        val carousel = Carousel(4, selected = 1)
        assertEquals(0f, carousel.offset(1), 0f)
        assertEquals(1f, carousel.offset(2), 0f)
        assertEquals(-1f, carousel.offset(0), 0f)
    }

    @Test
    fun theNextPlaneSlidesToTheCentre() {
        val carousel = Carousel(4)
        carousel.advance(1, 0.05f)
        assertTrue(carousel.offset(1) in 0.01f..0.99f)
        repeat(60) { carousel.advance(1, 0.05f) }
        assertEquals(0f, carousel.offset(1), 0.001f)
    }

    @Test
    fun theLastPlaneNeighboursTheFirstAndTheShortWayIsTaken() {
        val carousel = Carousel(4)
        carousel.advance(3, 0.05f)
        // plane 3 is one step to the left of plane 0, so the carousel turns left, not three steps right
        assertTrue(carousel.offset(3) < 0f && carousel.offset(3) > -1f)
        repeat(60) { carousel.advance(3, 0.05f) }
        assertEquals(0f, carousel.offset(3), 0.001f)
    }

    @Test
    fun noPlanesIsHarmless() {
        val carousel = Carousel(0)
        carousel.advance(0, 0.1f)
        assertEquals(0f, carousel.offset(0), 0f)
    }
}
