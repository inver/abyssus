/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CancellationException

class CancellationTest {
    @Test
    fun ordinaryFailuresAreCaptured() {
        val r = runCatchingKeepingCancellation { error("bad file") }
        assertTrue(r.isFailure)
        assertEquals("bad file", r.exceptionOrNull()!!.message)
        assertEquals(1, runCatchingKeepingCancellation { 1 }.getOrNull())
    }

    @Test
    fun cancellationIsNotReportedAsAFailure() {
        try {
            runCatchingKeepingCancellation { throw CancellationException() }
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
        }
    }
}
