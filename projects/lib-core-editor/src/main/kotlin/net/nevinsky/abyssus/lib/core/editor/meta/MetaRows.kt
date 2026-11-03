/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.meta

import net.nevinsky.abyssus.lib.core.editor.util.JsonValues
import net.nevinsky.abyssus.lib.core.editor.util.scalarText
import net.nevinsky.abyssus.lib.core.editor.util.valuesOf
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class RowKind { FIELD, HEADING, ADDITIONAL }

/** One line of the properties table: [name] and the already formatted [value] (empty for a heading). */
data class PropertyRow(val name: String, val value: String, val kind: RowKind = RowKind.FIELD)

private val LAST_MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * The rows of an asset's `meta.json`: its top-level fields in file order (`additional` excluded), then an `additional`
 * heading and the fields of `additional`. Every field present is listed, whatever the asset type.
 */
fun metaRowsOf(meta: JsonValues, messages: EditorMessages, zone: ZoneId = ZoneId.systemDefault()): List<PropertyRow> {
    val rows = mutableListOf<PropertyRow>()
    val additional = meta[ADDITIONAL]
    for ((name, value) in meta) {
        if (name == ADDITIONAL) continue
        rows += PropertyRow(name, valueText(name, value, zone, messages))
    }
    if (meta.containsKey(ADDITIONAL)) {
        rows += PropertyRow(messages.message("propertiesAdditional"), "", RowKind.HEADING)
        val block = valuesOf(additional)
        if (block != null) {
            for ((name, value) in block) rows += PropertyRow(name, valueText(name, value, zone, messages), RowKind.ADDITIONAL)
        } else {
            rows += PropertyRow(ADDITIONAL, valueText(ADDITIONAL, additional, zone, messages), RowKind.ADDITIONAL)
        }
    }
    return rows
}

const val ADDITIONAL = "additional"

private fun valueText(name: String, value: Any?, zone: ZoneId, messages: EditorMessages): String = when {
    value is List<*> -> messages.message("propertiesListSummary", value.size)
    value is Map<*, *> -> messages.message("propertiesObjectSummary", value.size)
    name == "lastModified" && (value is Int || value is Long) -> LAST_MODIFIED.format(Instant.ofEpochMilli((value as Number).toLong()).atZone(zone))
    value == null -> messages.message("dtoNullValue")
    else -> scalarText(value)
}
