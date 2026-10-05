/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.json

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * Binds native JSON (`.abss`, `.scene`, asset `meta.json`) to Kotlin classes: unknown properties are skipped, unknown
 * enum values take their default, and properties keep declaration order. Create one and pass it to what needs it.
 */
class JsonProcessor {
    private val format = JsonFormat()

    private val mapper: JsonMapper = format.mapperBuilder()
        .addModule(KotlinModule.Builder().build())
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        .build()

    private val prettyPrinter = format.prettyPrinter

    /** [text] as a JSON tree; throws when it is not a JSON object. */
    fun readObject(text: String): JsonNode =
        mapper.readTree(text)?.takeIf { it.isObject } ?: throw IllegalArgumentException("expected a JSON object")

    //todo add validation on parsing for formats
    fun <T> parse(text: String, clazz: Class<T>): T {
        return mapper.readValue(text, clazz)
    }

    /** Binds an already parsed [node] to [clazz], with the same rules as [parse]. */
    fun <T> bind(node: JsonNode, clazz: Class<T>): T = mapper.treeToValue(node, clazz)

    /** [obj] as one line of JSON, with no trailing newline. */
    fun compact(obj: Any): String = mapper.writeValueAsString(obj)

    fun pretty(obj: Any): String = mapper.writer(prettyPrinter).writeValueAsString(obj) + "\n"
}
