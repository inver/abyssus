/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReloadPolicyTest {
    @Test
    fun typingIsDelayedAndTheDelayedReloadIsDueOnce() {
        val p = ReloadPolicy()
        assertEquals(Reload.LATER, p.typing())
        assertEquals(Reload.LATER, p.typing())
        assertTrue(p.due())
        assertFalse(p.due())
    }

    @Test
    fun anImmediateChangeReloadsNowAndDropsTheDelayedReload() {
        val p = ReloadPolicy()
        p.typing()
        assertEquals(Reload.NOW, p.immediate())
        assertFalse(p.due())
    }

    @Test
    fun nothingDelayedMeansNothingDue() {
        assertFalse(ReloadPolicy().due())
    }

    @Test
    fun anImmediateChangeAfterTheDelayedOneHasRunStillReloadsNow() {
        val p = ReloadPolicy()
        p.typing()
        assertTrue(p.due())
        assertEquals(Reload.NOW, p.immediate())
    }

    @Test
    fun disposalDropsPendingWorkAndRefusesNewEvents() {
        val p = ReloadPolicy()
        p.typing()
        p.dispose()
        assertFalse(p.due())
        assertEquals(Reload.NEVER, p.typing())
        assertEquals(Reload.NEVER, p.immediate())
        assertFalse(p.due())
    }
}
