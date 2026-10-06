/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

class ModelLoggingTest {
    private val original = ModelLogging.logger

    @After fun restore() { ModelLogging.logger = original }

    @Test fun theDefaultComesFromSlf4jSoAStandaloneUserNeedsNoSetup() {
        // with no SLF4J binding on the classpath SLF4J hands out its no-op logger; with one, a logger of this name
        assertTrue(original.name == "abyssus.model" || original === NOPLogger.NOP_LOGGER)
    }

    @Test fun anApplicationReplacesTheLoggerOnce() {
        val replacement: Logger = NOPLogger.NOP_LOGGER
        ModelLogging.logger = replacement
        assertSame(replacement, ModelLogging.logger)
    }
}
