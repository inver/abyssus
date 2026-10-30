/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.manifest

/** A numeric dotted version (`1.10.0`); build one with [parseVersion], so `1.10.0` sorts above `1.9.0`. */
class VersionOrder internal constructor(private val parts: List<Long>) : Comparable<VersionOrder> {
    override fun compareTo(other: VersionOrder): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val c = parts.getOrElse(i) { 0L }.compareTo(other.parts.getOrElse(i) { 0L })
            if (c != 0) return c
        }
        return 0
    }

    override fun equals(other: Any?) = other is VersionOrder && compareTo(other) == 0

    override fun hashCode() = parts.dropLastWhile { it == 0L }.hashCode()

    override fun toString() = parts.joinToString(".")
}

private val numericVersion = Regex("""\d{1,9}(\.\d{1,9})*""")

/** The parsed version, or `null` when [text] is not numeric dotted. */
fun parseVersionOrNull(text: String): VersionOrder? =
    if (numericVersion.matches(text)) VersionOrder(text.split('.').map { it.toLong() }) else null

/** The parsed version; throws [IllegalArgumentException] when [text] is not numeric dotted. */
fun parseVersion(text: String): VersionOrder =
    parseVersionOrNull(text) ?: throw IllegalArgumentException("Not a numeric dotted version: '$text'")
