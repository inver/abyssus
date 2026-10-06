/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.document

import net.nevinsky.abyssus.core.io.JsonProcessor

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper

/**
 * How native JSON is read and written, in one place: unknown properties are skipped, properties keep declaration
 * order, and the pretty printer indents by two spaces with `"key": value` and empty containers closed on the same line.
 * [JsonProcessor] and the plugin's `SceneJson` both build on it, so the two cannot drift apart.
 */
class JsonFormat {
    /** A mapper builder with the shared reading settings; the caller adds whatever else it needs. */
    fun mapperBuilder(): JsonMapper.Builder = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)

    val prettyPrinter: DefaultPrettyPrinter = DefaultPrettyPrinter(
        Separators.createDefaultInstance()
            .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
            .withObjectEmptySeparator("")
            .withArrayEmptySeparator(""),
    ).also {
        val indenter = DefaultIndenter("  ", "\n")
        it.indentObjectsWith(indenter)
        it.indentArraysWith(indenter)
    }
}
