/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LightingRefreshPolicyTest {
    private val policy = LightingRefreshPolicy()

    private fun sun(degrees: Double) = Vector3(sin(Math.toRadians(degrees)).toFloat(), cos(Math.toRadians(degrees)).toFloat(), 0f)

    private fun frame(
        time: Double = 0.0, sun: Vector3 = sun(0.0), revision: Long = 1, clouds: Boolean = true, drifting: Boolean = true,
        visible: Boolean = true,
    ) = LightingFrame(sun, time, revision, clouds, drifting, visible)

    /** Runs the first build to completion at time 0 and returns the policy idle with a current environment. */
    private fun built(): LightingRefreshPolicy {
        val ticket = policy.update(frame()).build
        assertNotNull(ticket)
        assertEquals(Completion.PUBLISH, policy.completed(ticket!!))
        return policy
    }

    @Test
    fun theFirstFrameStartsABuildAndItsResultIsUsedAtOnce() {
        val step = policy.update(frame())
        assertNotNull(step.build)
        assertTrue(policy.isBuilding)
        assertEquals(Completion.PUBLISH, policy.completed(step.build!!))
        assertFalse(policy.isFading)
        assertNull(policy.update(frame(time = 0.1)).build)
    }

    @Test
    fun theSunMustMoveMoreThanADegree() {
        built()
        assertNull(policy.update(frame(time = 0.1, sun = sun(0.9))).build)
        assertNotNull(policy.update(frame(time = 0.2, sun = sun(1.2))).build)
    }

    @Test
    fun driftingCloudsRefreshEveryTwoSecondsOfCloudTime() {
        built()
        assertNull(policy.update(frame(time = 1.9)).build)
        assertNotNull(policy.update(frame(time = 2.0)).build)
    }

    @Test
    fun stationaryCloudsAreNeverRefreshedForTime() {
        built()
        assertNull(policy.update(frame(time = 60.0, drifting = false)).build)
        assertNull(policy.update(frame(time = 600.0, drifting = false)).build)
    }

    @Test
    fun noCloudsMeansNoTimeRefresh() {
        val ticket = policy.update(frame(clouds = false)).build!!
        policy.completed(ticket)
        assertNull(policy.update(frame(time = 30.0, clouds = false)).build)
    }

    @Test
    fun aCloudReloadRefreshesEvenWithoutWindOrMovement() {
        built()
        assertNull(policy.update(frame(time = 5.0, drifting = false)).build)
        assertNotNull(policy.update(frame(time = 5.1, drifting = false, revision = 2)).build)
    }

    @Test
    fun cloudVisibilityChangesRefresh() {
        built()
        assertNotNull(policy.update(frame(time = 0.1, clouds = false)).build)
    }

    @Test
    fun aFinishedBuildFadesInOverOneSecond() {
        built()
        val ticket = policy.update(frame(time = 2.0)).build!!
        assertEquals(Completion.FADE, policy.completed(ticket))
        assertTrue(policy.isFading)
        assertEquals(0f, policy.update(frame(time = 2.0)).weight, 1e-6f)
        assertEquals(0.5f, policy.update(frame(time = 2.5)).weight, 1e-6f)
        val end = policy.update(frame(time = 3.0))
        assertTrue(end.promoted)
        assertEquals(0f, end.weight, 0f)
        assertFalse(policy.isFading)
    }

    @Test
    fun nothingStartsWhileBuildingOrFadingAndRequestsCoalesce() {
        built()
        val ticket = policy.update(frame(time = 2.0)).build!!
        // the sun runs away meanwhile: no second build is started, and none is queued
        assertNull(policy.update(frame(time = 2.1, sun = sun(10.0))).build)
        assertNull(policy.update(frame(time = 2.2, sun = sun(20.0))).build)
        assertEquals(Completion.FADE, policy.completed(ticket))
        assertNull(policy.update(frame(time = 2.5, sun = sun(30.0))).build)
        // once the fade ends, one build answers the latest sun, not the intermediate ones
        val after = policy.update(frame(time = 3.3, sun = sun(40.0)))
        assertTrue(after.promoted)
        assertNotNull(after.build)
        assertTrue(policy.isBuilding)
    }

    @Test
    fun anObsoleteBuildIsAbandonedAndRestarted() {
        built()
        val old = policy.update(frame(time = 2.0)).build!!
        val step = policy.update(frame(time = 2.1, revision = 2))
        assertTrue(step.abandon)
        assertNotNull(step.build)
        assertEquals(Completion.DISCARD, policy.completed(old))
        assertEquals(Completion.FADE, policy.completed(step.build!!))
    }

    @Test
    fun aResultForAnOutdatedRevisionIsRejectedEvenIfNoFrameCameBetween() {
        built()
        val ticket = policy.update(frame(time = 2.0)).build!!
        policy.update(frame(time = 2.0, revision = 3))
        assertEquals(Completion.DISCARD, policy.completed(ticket))
    }

    @Test
    fun aHiddenViewStartsNothingAndDoesNotFade() {
        built()
        val ticket = policy.update(frame(time = 2.0)).build!!
        policy.completed(ticket)
        assertEquals(0.2f, policy.update(frame(time = 2.2)).weight, 1e-6f)
        assertEquals(0.2f, policy.update(frame(time = 50.0, visible = false)).weight, 1e-6f)
        assertNull(policy.update(frame(time = 60.0, visible = false, revision = 9)).build)
        // visible again: the fade continues from where it paused, with one frame's time
        assertEquals(0.3f, policy.update(frame(time = 60.1)).weight, 1e-6f)
    }

    @Test
    fun aFailedBuildIsNotRetriedUntilSomethingChanges() {
        val ticket = policy.update(frame()).build!!
        policy.failed(ticket)
        assertFalse(policy.isBuilding)
        assertNull(policy.update(frame(time = 1.0)).build)
        assertNotNull(policy.update(frame(time = 2.0)).build)
    }

    @Test
    fun aFailureAfterALaterBuildKeepsTheLastLighting() {
        built()
        val ticket = policy.update(frame(time = 2.0)).build!!
        policy.failed(ticket)
        assertFalse(policy.isFading)
        assertEquals(Completion.DISCARD, policy.completed(ticket))
        assertNull(policy.update(frame(time = 3.0)).build)
    }

    @Test
    fun resetForgetsEverythingAndTheNextFrameBuildsAgain() {
        built()
        policy.reset()
        val ticket = policy.update(frame(time = 0.5)).build
        assertNotNull(ticket)
        assertEquals(Completion.PUBLISH, policy.completed(ticket!!))
    }

    @Test
    fun anUnknownTicketIsDiscarded() {
        built()
        assertEquals(Completion.DISCARD, policy.completed(99))
    }

    @Test
    fun withoutAFadeAFinishedBuildIsUsedAtOnceEvenWhileTimeStandsStill() {
        val still = LightingRefreshPolicy(fadeSeconds = 0.0)
        val first = still.update(frame()).build!!
        assertEquals(Completion.PUBLISH, still.completed(first))
        val second = still.update(frame(sun = sun(10.0))).build!!
        assertEquals(Completion.PUBLISH, still.completed(second))
        assertFalse(still.isFading)
        assertNotNull(still.update(frame(sun = sun(30.0))).build)
    }
}
