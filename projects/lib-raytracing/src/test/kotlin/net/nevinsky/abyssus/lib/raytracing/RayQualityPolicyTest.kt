/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Test

class RayQualityPolicyTest {
    @Test fun interactionHasReducedDimensionsAndOneSampleWhileStableFastFramesImprove() {
        val policy = RayQualityPolicy()
        val moving = policy.choose(1280, 720, stableFrames = 0)
        assertEquals(640, moving.width); assertEquals(360, moving.height); assertEquals(1, moving.samples)
        repeat(20) { policy.observe(5_000_000L); policy.choose(1280, 720, stableFrames = 20) }
        val stable = policy.choose(1280, 720, stableFrames = 20)
        assertTrue(stable.width > moving.width); assertTrue(stable.samples > 1)
        val movedAgain = policy.choose(1280, 720, stableFrames = 0)
        assertEquals(640, movedAgain.width); assertEquals(1, movedAgain.samples)
    }

    @Test fun slowTimingsReduceResolutionButNeverGoBelowTheDeclaredFloor() {
        val policy = RayQualityPolicy()
        repeat(20) { policy.observe(5_000_000L); policy.choose(1280, 720, 20) }
        assertTrue(policy.choose(1280, 720, 20).samples > 1)
        repeat(50) { policy.observe(200_000_000L); policy.choose(1280, 720, 20) }
        assertEquals(640, policy.choose(1280, 720, 20).width)
        assertEquals(360, policy.choose(1280, 720, 20).height)
        assertEquals(1, policy.choose(1280, 720, 20).samples)
    }

    @Test fun pixelMemoryDimensionAndRayCapsBoundEveryPlanAndRejectAnImpossibleFloor() {
        val policy = RayQualityPolicy(RayQualityLimits(maxDimension = 800, maxPixels = 400_000, frameMemoryBytes = 24_000_000, maxRaysPerFrame = 500_000))
        repeat(20) { policy.observe(1_000_000L) }
        val plan = policy.choose(1280, 720, 20)
        assertTrue(plan.width <= 800 && plan.height <= 800)
        assertTrue(plan.width.toLong() * plan.height <= 400_000)
        assertTrue(plan.frameBytes <= 24_000_000)
        assertTrue(plan.width.toLong() * plan.height * plan.samples <= 500_000)
        assertThrows(RayQualityLimitException::class.java) { policy.choose(4000, 3000, 0) }
    }

    @Test fun tinyFramebuffersRemainValidAndInvalidBoundsOrTimingsAreRejected() {
        val policy = RayQualityPolicy()
        assertEquals(1, policy.choose(1, 1, 0).width)
        assertThrows(IllegalArgumentException::class.java) { policy.choose(0, 720, 0) }
        assertThrows(IllegalArgumentException::class.java) { policy.observe(-1) }
        assertThrows(IllegalArgumentException::class.java) { RayQualityLimits(frameMemoryBytes = 0) }
        assertThrows(IllegalArgumentException::class.java) { RayQualityLimits(minimumScale = Double.NaN) }
    }

    @Test fun secondaryVisibilityAndReflectionRayCostFitsThePerFrameBudgetWhenTheFrameCanShrink() {
        val policy = RayQualityPolicy()
        repeat(20) { policy.observe(1_000_000L) }
        val plan = policy.choose(1280, 720, 20, raysPerSample = 8)
        assertTrue(plan.width.toLong() * plan.height * plan.samples * 8 <= policy.limits.maxRaysPerFrame)
    }

    @Test fun aBudgetTooSmallForTheMinimumScaleShrinksTheFrameInsteadOfStopping() {
        val policy = RayQualityPolicy()
        val plan = policy.choose(1761, 1201, 20, raysPerSample = 96, maxRaysPerFrame = 33_554_432)
        assertTrue(plan.width < 880 && plan.width >= (1761 * policy.limits.budgetFloorScale).toInt())
        assertTrue(plan.width.toLong() * plan.height * plan.samples * 96 <= 33_554_432)
    }
    @Test fun anOverBudgetRayCostExplainsFallbackAtTheMinimumFrame() {
        val policy = RayQualityPolicy()
        for (stable in listOf(0,20)) {
            val error=assertThrows(RayQualityLimitException::class.java) { policy.choose(2560,1440,stable,raysPerSample=300) }
            assertTrue(error.message, error.message!!.contains("ray budget") && error.message!!.contains("512x288 pixels") && error.message!!.contains("needs at least 44236800"))
        }
        assertThrows(RayQualityLimitException::class.java) { RayQualityPolicy(RayQualityLimits(maxDimension=400)).choose(1920,1080,0) }
    }
    @Test fun currentSceneBudgetCanChangeWithoutRecreatingThePolicy() {
        val policy=RayQualityPolicy()
        assertThrows(RayQualityLimitException::class.java) { policy.choose(1280,720,0,8,maxRaysPerFrame=1) }
        val plan=policy.choose(1280,720,10,8,maxRaysPerFrame=67108864)
        assertTrue(plan.width.toLong()*plan.height*plan.samples*8<=67108864)
    }
}
