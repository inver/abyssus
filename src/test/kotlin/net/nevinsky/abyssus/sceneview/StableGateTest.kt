/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StableGateTest {
    private val hold = 250_000_000L

    @Test
    fun opensOnlyAfterBeingOkForTheWholeHoldTime() {
        val gate = StableGate(hold)
        assertFalse(gate.update(true, 1_000))
        assertFalse(gate.update(true, 1_000 + hold - 1))
        assertTrue(gate.update(true, 1_000 + hold))
        assertTrue(gate.update(true, 1_000 + 5 * hold))
    }

    @Test
    fun anyNotOkResetsTheClock() {
        val gate = StableGate(hold)
        gate.update(true, 0)
        assertTrue(gate.update(true, hold))
        assertFalse(gate.update(false, hold + 1))
        assertFalse(gate.update(true, hold + 2)) // restarted
        assertFalse(gate.update(true, hold + 2 + hold - 1))
        assertTrue(gate.update(true, hold + 2 + hold))
    }

    @Test
    fun neverOpensWhileNotOk() {
        val gate = StableGate(hold)
        for (t in 0L..10L) assertFalse(gate.update(false, t * hold))
    }
}
