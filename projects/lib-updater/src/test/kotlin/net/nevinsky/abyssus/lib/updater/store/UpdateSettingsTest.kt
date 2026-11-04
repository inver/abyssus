/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.store

import java.nio.file.Files
import net.nevinsky.abyssus.lib.updater.release.CachedResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val file get() = tmp.root.toPath().resolve("settings.properties")

    private fun settings(env: Map<String, String> = emptyMap(), property: String? = null, default: Boolean = true) =
        UpdateSettings(file, env, { if (it == "controlLine.updates") property else null }, default)

    @Test
    fun `default applies without any setting`() {
        assertTrue(settings().enabled())
        assertFalse(settings(default = false).enabled())
    }

    @Test
    fun `file environment and property each turn updates off`() {
        Files.writeString(file, "enabled=false\n")
        assertFalse(settings().enabled())
        Files.delete(file)
        assertFalse(settings(env = mapOf("CONTROL_LINE_UPDATES" to "off")).enabled())
        assertFalse(settings(property = "OFF").enabled())
    }

    @Test
    fun `on in the file turns updates on over an off default but not over environment off`() {
        Files.writeString(file, "enabled=true\n")
        assertTrue(settings(default = false).enabled())
        assertFalse(settings(env = mapOf("CONTROL_LINE_UPDATES" to "off"), default = false).enabled())
    }

    @Test
    fun `response cache round trips`() {
        val cache = FileResponseCache(tmp.root.toPath().resolve("cache"))
        assertNull(cache.load())
        cache.store(CachedResponse("\"abc\"", "[1]".toByteArray()))
        val loaded = FileResponseCache(tmp.root.toPath().resolve("cache")).load()!!
        assertEquals("\"abc\"", loaded.etag)
        assertEquals("[1]", String(loaded.body))
    }
}
