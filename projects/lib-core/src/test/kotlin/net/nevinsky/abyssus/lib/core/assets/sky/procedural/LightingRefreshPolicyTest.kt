/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LightingRefreshPolicyTest {
    private fun request(angle: Double = 0.0, time: Double = 0.0, drift: Boolean = false) =
        LightingRefreshRequest(
            LightingSunDirection(sin(Math.toRadians(angle)), cos(Math.toRadians(angle)), 0.0),
            LightingRevision("sky", 1, "cloud", 1, true), time, drift)

    private fun first(policy: LightingRefreshPolicy, request: LightingRefreshRequest = request()): LightingBuildRequest {
        val build = policy.update(request, true, 0.0).build!!
        assertTrue(policy.completed(build))
        assertEquals(build, policy.state.current)
        assertNull(policy.state.next)
        return build
    }

    @Test fun firstBuildPublishesImmediatelyAndOneDegreeStartsRefresh() {
        val policy = LightingRefreshPolicy()
        first(policy)
        assertNull(policy.update(request(angle = 0.99), true, 0.0).build)
        val build = policy.update(request(angle = 1.0), true, 0.0).build!!
        assertTrue(policy.completed(build))
        assertEquals(build, policy.state.next)
        assertEquals(0.0, policy.state.fadeWeight, 0.0)
        assertEquals(0.5, policy.update(build.request, true, 0.5).fadeWeight, 1e-9)
        assertEquals(build, policy.update(build.request, true, 0.5).current)
        assertNull(policy.state.next)
    }

    @Test fun onlyVisibleDriftingCloudsRefreshEveryTwoCloudSeconds() {
        val policy = LightingRefreshPolicy()
        first(policy, request(drift = true))
        assertNull(policy.update(request(time = 1.999, drift = true), true, 0.0).build)
        assertNotNull(policy.update(request(time = 2.0, drift = true), true, 0.0).build)
        val stationary = LightingRefreshPolicy()
        first(stationary)
        assertNull(stationary.update(request(time = 100.0), true, 0.0).build)
        val invisible = request(drift = true).let { it.copy(revision = it.revision.copy(cloudsVisible = false)) }
        val hiddenClouds = LightingRefreshPolicy()
        first(hiddenClouds, invisible)
        assertNull(hiddenClouds.update(invisible.copy(cloudTimeSeconds = 100.0), true, 0.0).build)
    }

    @Test fun stationaryCloudReloadIdentityAndVisibilityInvalidate() {
        for (revision in listOf(
            request().revision.copy(cloudRevision = 2),
            request().revision.copy(cloudIdentity = "other"),
            request().revision.copy(cloudsVisible = false),
            request().revision.copy(skyRevision = 2),
            request().revision.copy(skyIdentity = "other"))) {
            val policy = LightingRefreshPolicy()
            first(policy)
            assertEquals(revision, policy.update(request().copy(revision = revision), true, 0.0).build!!.request.revision)
        }
    }

    @Test fun hiddenViewPausesBuildAndFadeAndCoalescesWithoutWork() {
        val policy = LightingRefreshPolicy()
        assertNull(policy.update(request(), false, 50.0).build)
        first(policy)
        val build = policy.update(request(angle = 2.0), true, 0.0).build!!
        assertFalse(policy.update(build.request, false, 50.0).canStepBuild)
        assertEquals(build, policy.state.build)
        policy.update(build.request, true, 0.0)
        policy.completed(build)
        assertEquals(0.25, policy.update(build.request, true, 0.25).fadeWeight, 0.0)
        assertEquals(0.25, policy.update(request(angle = 8.0), false, 50.0).fadeWeight, 0.0)
        assertNull(policy.state.build)
        val resumed = policy.update(request(angle = 8.0), true, 0.75)
        assertEquals(build, resumed.current)
        assertEquals(request(angle = 8.0), resumed.build!!.request)
    }

    @Test fun newerRequestsCoalesceDuringBuildAndFade() {
        val policy = LightingRefreshPolicy()
        first(policy)
        val build = policy.update(request(angle = 2.0), true, 0.0).build!!
        repeat(100) { assertEquals(build, policy.update(request(angle = 3.0 + it), true, 0.0).build) }
        assertTrue(policy.completed(build))
        assertNull(policy.update(request(angle = 120.0), true, 0.5).build)
        val state = policy.update(request(angle = 140.0), true, 0.5)
        assertEquals(build, state.current)
        assertNull(state.next)
        assertEquals(request(angle = 140.0), state.build!!.request)
    }

    @Test fun obsoleteRevisionAndContextCompletionsCannotPublish() {
        val policy = LightingRefreshPolicy()
        val old = first(policy)
        val build = policy.update(request(angle = 2.0), true, 0.0).build!!
        val revised = request().copy(revision = request().revision.copy(cloudRevision = 2))
        val replacement = policy.update(revised, true, 0.0).build!!
        assertFalse(policy.completed(build))
        assertFalse(policy.failed(build))
        assertEquals(old, policy.state.current)
        assertTrue(policy.completed(replacement))
        policy.resetContext()
        assertNull(policy.state.current)
        assertNull(policy.state.next)
        assertNull(policy.state.build)
        val restored = policy.update(revised, true, 0.0).build!!
        assertNotEquals(replacement.id, restored.id)
        assertFalse(policy.completed(replacement))
        assertTrue(policy.completed(restored))
        assertEquals(restored, policy.state.current)
    }

    @Test fun revisionChangeDropsObsoleteFadeButRetainsCurrent() {
        val policy = LightingRefreshPolicy()
        val current = first(policy)
        val next = policy.update(request(angle = 2.0), true, 0.0).build!!
        policy.completed(next)
        val revised = request().copy(revision = request().revision.copy(cloudRevision = 2))
        val state = policy.update(revised, true, 0.5)
        assertEquals(current, state.current)
        assertNull(state.next)
        assertEquals(revised, state.build!!.request)
    }

    @Test fun errorsKeepCurrentAndRetryWithBoundedVisibleTimeBackoff() {
        val policy = LightingRefreshPolicy(maxRetries = 2, retryDelaySeconds = 2.0)
        val current = first(policy)
        val target = request(angle = 2.0)
        var build = policy.update(target, true, 0.0).build!!
        repeat(2) {
            assertTrue(policy.failed(build))
            assertEquals(current, policy.state.current)
            assertNull(policy.update(target, false, 100.0).build)
            assertNull(policy.update(target, true, 1.999).build)
            build = policy.update(target, true, 0.001).build!!
        }
        policy.failed(build)
        assertNull(policy.update(target, true, 100.0).build)
        val revised = target.copy(revision = target.revision.copy(cloudRevision = 2))
        val recovery = policy.update(revised, true, 0.0).build!!
        assertTrue(policy.completed(recovery))
        assertEquals(current, policy.state.current)
        assertEquals(recovery, policy.state.next)
    }

    @Test fun firstFailureKeepsAmbientUntilRetrySucceeds() {
        val policy = LightingRefreshPolicy()
        val build = policy.update(request(), true, 0.0).build!!
        policy.failed(build)
        assertNull(policy.state.current)
        val retry = policy.update(request(), true, 2.0).build!!
        policy.completed(retry)
        assertEquals(retry, policy.state.current)
    }
}
