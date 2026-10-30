/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.store

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Whether update checks run. Updates are off when the system property `controlLine.updates`, the environment variable
 * `CONTROL_LINE_UPDATES` or `enabled` in `settings.properties` says `off`/`false`/`0`/`no`; an explicit `on` in the
 * file turns them on over [defaultEnabled]. Off means no request at all.
 */
class UpdateSettings(
    private val file: Path,
    private val environment: Map<String, String>,
    private val systemProperty: (String) -> String?,
    private val defaultEnabled: Boolean = true,
) {
    fun enabled(): Boolean {
        val off = setOf("off", "false", "0", "no")
        if (systemProperty("controlLine.updates")?.trim()?.lowercase() in off) return false
        if (environment["CONTROL_LINE_UPDATES"]?.trim()?.lowercase() in off) return false
        val fromFile = readFile()?.trim()?.lowercase()
        return when (fromFile) {
            null, "" -> defaultEnabled
            in off -> false
            "on", "true", "1", "yes" -> true
            else -> defaultEnabled
        }
    }

    private fun readFile(): String? = try {
        if (Files.isRegularFile(file)) Files.newInputStream(file).use { Properties().apply { load(it) }.getProperty("enabled") } else null
    } catch (e: IOException) {
        null
    }
}
