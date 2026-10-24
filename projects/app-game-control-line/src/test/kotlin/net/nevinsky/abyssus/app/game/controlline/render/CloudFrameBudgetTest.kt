/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.simplerThan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFrameBudgetTest {
    @Test
    fun fastFramesNeverTrip() {
        val budget = CloudFrameBudget()
        repeat(1000) { assertFalse(budget.frame(1f / 60f, volumetric = true)) }
    }

    @Test
    fun twoSecondsOverBudgetTrips() {
        val budget = CloudFrameBudget()
        var tripped = 0
        repeat(39) { if (budget.frame(0.05f, true)) tripped++ }
        assertEquals("39 frames of 50 ms are just under two seconds", 0, tripped)
        assertTrue(budget.frame(0.05f, true))
    }

    @Test
    fun aFastFrameBreaksTheRunAndAPauseDoesNot() {
        val budget = CloudFrameBudget()
        repeat(30) { budget.frame(0.05f, true) }
        assertFalse(budget.frame(0.01f, true))
        repeat(30) { assertFalse(budget.frame(0.05f, true)) }
        repeat(5) { assertFalse(budget.frame(1.0f, true)) }
        repeat(9) { assertFalse(budget.frame(0.05f, true)) }
        assertTrue("the pause neither counted nor broke the run", budget.frame(0.05f, true))
    }

    @Test
    fun otherTechniquesAreNotWatched() {
        val budget = CloudFrameBudget()
        repeat(200) { assertFalse(budget.frame(0.1f, volumetric = false)) }
    }

    @Test
    fun theStepDownChainEndsAtLayered() {
        val chain = generateSequence(CloudTechnique.VOLUMETRIC, ::simplerThan).toList()
        assertEquals(listOf(CloudTechnique.VOLUMETRIC, CloudTechnique.SHELLS, CloudTechnique.CUMULUS, CloudTechnique.LAYERED), chain)
    }

    @Test
    fun slowFramesStepTheSkyDownOneTechniqueAtATimeToLayered() {
        val step = CloudStepDown()
        var drawn: CloudTechnique? = CloudTechnique.VOLUMETRIC
        val seen = mutableListOf<CloudTechnique>()
        repeat(1000) {
            step.frame(0.05f, drawn)?.let { next -> seen += next; drawn = next }
        }
        assertEquals(listOf(CloudTechnique.SHELLS, CloudTechnique.CUMULUS, CloudTechnique.LAYERED), seen)
        assertEquals(CloudTechnique.LAYERED, drawn)
    }

    @Test
    fun aFastGameKeepsItsClouds() {
        val step = CloudStepDown()
        repeat(1000) { assertEquals(null, step.frame(1f / 60f, CloudTechnique.VOLUMETRIC)) }
        repeat(1000) { assertEquals(null, step.frame(0.5f, CloudTechnique.LAYERED)) }
        repeat(1000) { assertEquals(null, step.frame(0.5f, null)) }
    }
}
