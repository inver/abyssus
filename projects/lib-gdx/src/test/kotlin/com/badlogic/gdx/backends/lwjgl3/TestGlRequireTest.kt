/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package com.badlogic.gdx.backends.lwjgl3

import org.junit.Assert.*
import org.junit.Test

/** Spec `game-distribution`, "No context on the runner": a required GL test fails instead of being skipped. */
class TestGlRequireTest {
    @Test fun requiredWithoutADisplayFails() {
        val failure = assertThrows(AssertionError::class.java) { TestGl.enabledFor("true", "true", headless = true) }
        assertTrue(failure.message!!.contains("no display"))
    }

    @Test fun requiredWithoutOptingInFails() {
        assertThrows(AssertionError::class.java) { TestGl.enabledFor(null, "true", headless = false) }
    }

    @Test fun withoutRequireAMissingDisplaySkips() {
        assertFalse(TestGl.enabledFor("true", null, headless = true))
        assertFalse(TestGl.enabledFor(null, null, headless = false))
        assertTrue(TestGl.enabledFor("true", null, headless = false))
        assertTrue(TestGl.enabledFor("true", "true", headless = false))
    }
}
