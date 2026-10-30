/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.log

import com.intellij.openapi.diagnostic.Logger
import org.junit.Assert.*
import org.junit.Test

class IntellijLoggerTest {
    /** The IDE logger's abstract core, recording every call. */
    private class FakeIde(var debug: Boolean = true) : Logger() {
        val calls = mutableListOf<String>()
        val errors = mutableListOf<Throwable?>()
        override fun isDebugEnabled() = debug
        override fun debug(message: String?, t: Throwable?) { calls += "debug $message"; errors += t }
        override fun info(message: String?, t: Throwable?) { calls += "info $message"; errors += t }
        override fun warn(message: String?, t: Throwable?) { calls += "warn $message"; errors += t }
        override fun error(message: String?, t: Throwable?, vararg details: String) { calls += "error $message"; errors += t }
    }

    @Test fun everyLevelReachesTheIdeLoggerAndErrorIsDowngradedToWarn() {
        val ide = FakeIde()
        val log = IntellijLogger("Abyssus.test", ide)
        log.info("hello {}", "world")
        log.warn("careful")
        log.error("a problem with one asset, not an IDE error")
        log.debug("detail {} {}", 1, 2)
        assertEquals(listOf("info hello world", "warn careful", "warn a problem with one asset, not an IDE error", "debug detail 1 2"), ide.calls)
    }

    @Test fun aThrowableTravelsWithTheMessage() {
        val ide = FakeIde()
        val boom = IllegalStateException("boom")
        IntellijLogger("Abyssus.test", ide).warn("failed to load", boom)
        assertEquals(listOf("warn failed to load"), ide.calls)
        assertSame(boom, ide.errors.single())
    }

    @Test fun lazyDebugIsOnlyBuiltWhenTheIdeCategoryHasDebugEnabled() {
        val ide = FakeIde(debug = false)
        val log = IntellijLogger("Abyssus.test", ide)
        assertFalse(log.isDebugEnabled)
        log.atDebug().log { error("must not be built while debug is off") }
        assertEquals(emptyList<String>(), ide.calls)
        ide.debug = true
        log.atDebug().log { "now wanted" }
        assertEquals(listOf("debug now wanted"), ide.calls)
    }

    @Test fun theFactoryNamesLoggersUnderItsPrefixAndReusesThem() {
        val factory = IntellijLoggerFactory("Abyssus")
        assertEquals("Abyssus.ray", factory.getLogger("ray").name)
        assertSame(factory.getLogger("ray"), factory.getLogger("ray"))
        assertEquals("Abyssus", factory.getLogger("").name)
    }
}
