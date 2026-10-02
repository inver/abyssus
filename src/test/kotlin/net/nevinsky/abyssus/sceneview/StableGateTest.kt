/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
