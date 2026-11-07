/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render

import net.nevinsky.abyssus.lib.render.post.SSAO_SAMPLES
import net.nevinsky.abyssus.lib.render.post.ssaoKernel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SsaoKernelTest {
    @Test
    fun thePointsLieInTheUnitHemisphereAboveTheSurface() {
        val kernel = ssaoKernel()
        assertEquals(SSAO_SAMPLES, kernel.size)
        for (p in kernel) {
            assertTrue("$p below the surface", p.z > 0f)
            assertTrue("$p too near the surface's plane", p.z >= 0.15f * p.len() - 1e-5f)
            assertTrue("$p outside the unit hemisphere", p.len() <= 1f + 1e-5f)
        }
    }

    @Test
    fun morePointsLieNearTheSurfaceAndTheKernelIsTheSameEveryRun() {
        val kernel = ssaoKernel()
        val near = kernel.count { it.len() < 0.5f }
        assertTrue("only $near of the points are near the surface", near >= SSAO_SAMPLES / 3)
        assertEquals(kernel, ssaoKernel())
    }
}
