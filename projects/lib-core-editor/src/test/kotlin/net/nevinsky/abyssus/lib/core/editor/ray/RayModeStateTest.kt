/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.editor.ray

import net.nevinsky.abyssus.lib.gdx.editor.ray.RayModeState
import net.nevinsky.abyssus.lib.gdx.editor.ray.RayModePhase
import net.nevinsky.abyssus.lib.gdx.editor.ray.RayBackendAttempt
import net.nevinsky.abyssus.lib.gdx.editor.ray.RayBackendSelection

import net.nevinsky.abyssus.lib.raytracing.*
import org.junit.Assert.*
import org.junit.Test

class RayModeStateTest {
    private val info = RayBackendInfo("Metal", "Test GPU")

    @Test fun offIsDefaultAndRequestPassesThroughCheckingPreparingAndActive() {
        val mode = RayModeState()
        assertEquals(RayModePhase.Off, mode.phase)
        assertFalse(mode.requested)
        assertFalse(mode.active)
        val revision = mode.request()
        assertEquals(RayModePhase.Checking, mode.phase)
        assertTrue(mode.requested)
        assertFalse(mode.active)
        mode.preparing(revision, info)
        assertEquals(RayModePhase.Preparing, mode.phase)
        assertEquals(info, mode.backendInfo)
        assertFalse(mode.active)
        mode.prepared(revision)
        assertEquals(RayModePhase.Active, mode.phase)
        assertTrue(mode.active)
    }

    @Test fun failureClearsActivationAndOnlyExplicitRetryRestartsChecking() {
        val mode = RayModeState()
        val revision = mode.request()
        mode.preparing(revision, info)
        mode.prepared(revision)
        mode.failed(revision, "Device lost")
        assertEquals(RayModePhase.Failed, mode.phase)
        assertFalse(mode.requested)
        assertFalse(mode.active)
        assertEquals("Device lost", mode.failure)
        assertEquals(info, mode.backendInfo)
        repeat(5) { mode.prepared(revision) }
        assertEquals(RayModePhase.Failed, mode.phase)
        val retry = checkNotNull(mode.retry())
        assertTrue(retry > revision)
        assertEquals(RayModePhase.Checking, mode.phase)
        assertNull(mode.failure)
        mode.preparing(retry, info)
        mode.prepared(retry)
        assertTrue(mode.active)
    }

    @Test fun unavailableCarriesReasonsAndCannotActivateOrRetry() {
        val mode = RayModeState()
        val revision = mode.request()
        val reason = RayBackendSelection.Unavailable(listOf(RayBackendAttempt("metal", RayUnavailableReason.RAY_QUERIES)))
        mode.unavailable(revision, reason)
        assertEquals(RayModePhase.Unavailable, mode.phase)
        assertFalse(mode.requested)
        assertFalse(mode.toggleEnabled)
        assertEquals(reason, mode.unavailable)
        assertNull(mode.retry())
        mode.prepared(revision)
        assertFalse(mode.active)
    }

    @Test fun offAndNewRequestIgnoreLatePreparationAndFailure() {
        val mode = RayModeState()
        val obsolete = mode.request()
        mode.off()
        mode.preparing(obsolete, info)
        mode.prepared(obsolete)
        mode.failed(obsolete, "Late failure")
        assertEquals(RayModePhase.Off, mode.phase)
        val current = mode.request()
        mode.preparing(current, info)
        mode.prepared(obsolete)
        mode.failed(obsolete, "Earlier request failed")
        assertEquals(RayModePhase.Preparing, mode.phase)
        mode.prepared(current)
        assertTrue(mode.active)
    }

    @Test fun hidingKeepsTheRequestAndInvalidatesOldWorkerTransitions() {
        val mode = RayModeState()
        val old = mode.request()
        mode.preparing(old, info); mode.prepared(old)
        val resumed = checkNotNull(mode.rechecking())
        assertTrue(mode.requested)
        assertEquals(RayModePhase.Checking, mode.phase)
        mode.failed(old, "Hidden old device")
        mode.prepared(old)
        assertEquals(RayModePhase.Checking, mode.phase)
        mode.preparing(resumed, info); mode.prepared(resumed)
        assertTrue(mode.active)
    }
}
