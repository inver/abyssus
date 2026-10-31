/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudTechniquesTest {
    @Test
    fun fallbackWalksFromVolumetricThroughShellsAndCumulusToLayered() {
        val chain = generateSequence(CloudTechnique.VOLUMETRIC, ::simplerThan).toList()
        assertEquals(
            listOf(CloudTechnique.VOLUMETRIC, CloudTechnique.SHELLS, CloudTechnique.CUMULUS, CloudTechnique.LAYERED),
            chain,
        )
        assertNull(simplerThan(CloudTechnique.LAYERED))
    }

    @Test
    fun aTechniqueThatCannotBeBuiltFallsBackToTheNextSimplerOne() {
        val built = mutableListOf<CloudTechnique>()
        val techniques = CloudTechniques("sky", org.slf4j.LoggerFactory.getLogger("test")) { technique ->
            built += technique
            if (technique == CloudTechnique.CUMULUS) error("no shader")
            object : CloudRenderer() {
                override fun draw(scene: CloudScene) = Unit
                override fun dispose() = Unit
            }
        }
        assertEquals(CloudTechnique.LAYERED, techniques.renderer(CloudTechnique.CUMULUS)?.first)
        assertEquals(listOf(CloudTechnique.CUMULUS, CloudTechnique.LAYERED), built)
    }
}
