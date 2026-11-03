/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.simplerThan
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudStepDownTest {
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
