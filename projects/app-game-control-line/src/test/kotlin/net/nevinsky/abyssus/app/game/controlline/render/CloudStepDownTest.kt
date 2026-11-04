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
        assertEquals(
            listOf(CloudTechnique.VOLUMETRIC, CloudTechnique.SHELLS, CloudTechnique.CUMULUS, CloudTechnique.LAYERED),
            chain,
        )
    }

    @Test
    fun slowFramesStepTheSkyDownOneTechniqueAtATimeToLayered() {
        val step = CloudStepDown()
        var drawn: CloudTechnique? = CloudTechnique.VOLUMETRIC
        val seen = mutableListOf<CloudTechnique>()
        repeat(1000) {
            step.frame(0.05f, drawn)?.let { next ->
                seen += next
                drawn = next
            }
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

    @Test
    fun atTheGamesFrameBudgetFortyFramesASecondStepDownAndSixtyDoNot() {
        // the reference machine held 35 to 40 frames a second with volumetric clouds, above the old 30 (evidence M2)
        val slow =
            CloudStepDown(net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudFrameBudget(CLOUD_STEP_DOWN_INTERVAL))
        var next: CloudTechnique? = null
        repeat(Math.round(2.1f * 40)) { slow.frame(1f / 40f, CloudTechnique.VOLUMETRIC)?.let { next = it } }
        assertEquals(CloudTechnique.SHELLS, next)

        val fast =
            CloudStepDown(net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudFrameBudget(CLOUD_STEP_DOWN_INTERVAL))
        // a vsynced 60 frames a second, with a late frame now and then
        repeat(600) { i ->
            assertEquals(
                null,
                fast.frame(
                    if (i % 7 == 0) {
                        0.0175f
                    } else {
                        1f / 60f
                    },
                    CloudTechnique.VOLUMETRIC,
                ),
            )
        }
    }

    @Test
    fun theOldDefaultKeepsFortyFramesASecond() {
        val step = CloudStepDown()
        repeat(200) { assertEquals(null, step.frame(1f / 40f, CloudTechnique.VOLUMETRIC)) }
    }
}
