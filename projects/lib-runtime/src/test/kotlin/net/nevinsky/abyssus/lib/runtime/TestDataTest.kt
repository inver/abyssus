/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class TestDataTest {
    @Test fun sharedFixtureIsAvailable() {
        assertTrue(testProject("Untitled").resolve("Untitled.abss").isFile)
    }
}
