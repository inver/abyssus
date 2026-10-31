/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.json

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Writes flat JSON objects for callers that hold no Jackson tree types: each value is text, a number or a boolean.
 * Writing JSON is `lib-core-editor`'s job; reading it into values is `lib-core`'s (`JsonObjectReader`).
 */
class JsonObjectWriter(private val mapper: ObjectMapper = ObjectMapper()) {
    /** The UTF-8 JSON object holding [members] in order. */
    fun bytes(members: Map<String, Any?>): ByteArray {
        for ((name, value) in members) when (value) {
            null, is String, is Int, is Long, is Double, is Float, is Boolean -> Unit
            else -> throw IllegalArgumentException("member $name is a ${value.javaClass.simpleName}")
        }
        return mapper.writeValueAsBytes(LinkedHashMap(members))
    }

    /** The JSON text of [value], quoted and escaped. */
    fun quoted(value: String): String = mapper.writeValueAsString(value)
}
