/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.meta

import net.nevinsky.abyssus.lib.gdx.editor.document.scalarOf
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.gdx.editor.EditorMessages
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
fun metaRowsOf(meta: JsonNode, messages: EditorMessages, zone: ZoneId = ZoneId.systemDefault()): List<PropertyRow> {
    val rows = mutableListOf<PropertyRow>()
    val additional = meta.get(ADDITIONAL)
    for ((name, value) in meta.properties()) {
        if (name == ADDITIONAL) continue
        rows += PropertyRow(name, valueText(name, value, zone, messages))
    }
    if (additional != null) {
        rows += PropertyRow(messages.message("propertiesAdditional"), "", RowKind.HEADING)
        if (additional.isObject) {
            for ((name, value) in additional.properties()) rows += PropertyRow(name, valueText(name, value, zone, messages), RowKind.ADDITIONAL)
        } else {
            rows += PropertyRow(ADDITIONAL, valueText(ADDITIONAL, additional, zone, messages), RowKind.ADDITIONAL)
        }
    }
    return rows
}

const val ADDITIONAL = "additional"

private fun valueText(name: String, value: JsonNode, zone: ZoneId, messages: EditorMessages): String = when {
    value.isArray -> messages.message("propertiesListSummary", value.size())
    value.isObject -> messages.message("propertiesObjectSummary", value.size())
    name == "lastModified" && value.isIntegralNumber -> LAST_MODIFIED.format(Instant.ofEpochMilli(value.longValue()).atZone(zone))
    else -> scalarOf(value)?.toString() ?: messages.message("dtoNullValue")
}
