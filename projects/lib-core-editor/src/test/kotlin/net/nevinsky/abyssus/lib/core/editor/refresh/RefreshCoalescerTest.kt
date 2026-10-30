/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshCoalescerTest {
    private val coalescer = RefreshCoalescer()

    @Test
    fun aBurstSchedulesOneRunWithEveryChangedKey() {
        assertTrue(coalescer.mark("a"))
        assertFalse(coalescer.mark("b"))
        assertFalse(coalescer.mark("a"))
        assertEquals(setOf("a", "b"), coalescer.begin())
        assertFalse(coalescer.finish())
        assertFalse(coalescer.pending)
    }

    @Test
    fun anEventDuringARebuildSchedulesAFollowUp() {
        coalescer.mark("a")
        coalescer.begin()
        assertFalse("the running rebuild owns the schedule", coalescer.mark("late"))
        assertTrue("the follow-up", coalescer.finish())
        assertEquals(setOf("late"), coalescer.begin())
        assertFalse(coalescer.finish())
    }

    @Test
    fun anIdleCoalescerSchedulesAgainAfterARun() {
        coalescer.mark("a")
        coalescer.begin()
        coalescer.finish()
        assertTrue(coalescer.mark("b"))
    }

    @Test
    fun disposalCancelsPendingWork() {
        assertTrue(coalescer.mark("a"))
        coalescer.dispose()
        assertNull("the scheduled run finds nothing to do", coalescer.begin())
        assertFalse("nothing schedules after disposal", coalescer.mark("b"))
        assertFalse(coalescer.pending)
    }

    @Test
    fun disposalDuringARebuildSchedulesNoFollowUp() {
        coalescer.mark("a")
        coalescer.begin()
        coalescer.mark("late")
        coalescer.dispose()
        assertFalse(coalescer.finish())
    }
}
