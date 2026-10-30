/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionOrderTest {
    @Test
    fun `numeric order not text order`() {
        assertTrue(parseVersion("1.10.0") > parseVersion("1.9.0"))
        assertTrue(parseVersion("2") > parseVersion("1.99.99"))
    }

    @Test
    fun `trailing zeros are equal`() {
        assertEquals(parseVersion("1.2"), parseVersion("1.2.0"))
        assertEquals(parseVersion("1.2").hashCode(), parseVersion("1.2.0").hashCode())
    }

    @Test
    fun `non numeric versions are rejected`() {
        for (bad in listOf("", "1.2.x", "v1.2", "1.2-beta", "1..2", "1.2.", "-1", "1.2.3+4", "99999999999")) {
            assertNull("'$bad'", parseVersionOrNull(bad))
        }
        try {
            parseVersion("1.0-rc1")
            throw AssertionError("expected a rejection")
        } catch (expected: IllegalArgumentException) {
        }
    }
}
