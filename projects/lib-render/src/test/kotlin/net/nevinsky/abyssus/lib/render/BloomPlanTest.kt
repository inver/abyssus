/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render

import net.nevinsky.abyssus.lib.render.post.BLOOM_LEVELS
import net.nevinsky.abyssus.lib.render.post.bloomPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomPlanTest {
    @Test
    fun theChainStartsAtHalfTheFrameAndHalves() {
        assertEquals(
            listOf(640 to 360, 320 to 180, 160 to 90, 80 to 45, 40 to 23),
            bloomPlan(1280, 720),
        )
        assertEquals(BLOOM_LEVELS, bloomPlan(1920, 1080).size)
    }

    @Test
    fun oddSizesRoundUpAndSmallFramesHaveFewerLevels() {
        assertEquals(listOf(3 to 2), bloomPlan(5, 3))
        assertEquals(listOf(8 to 8, 4 to 4, 2 to 2), bloomPlan(16, 16))
        assertTrue(bloomPlan(2, 2).isEmpty())
    }
}
