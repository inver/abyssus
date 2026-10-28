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
        val node = mapper.createObjectNode()
        for ((name, value) in members) when (value) {
            null -> node.putNull(name)
            is String -> node.put(name, value)
            is Int -> node.put(name, value)
            is Long -> node.put(name, value)
            is Double -> node.put(name, value)
            is Float -> node.put(name, value)
            is Boolean -> node.put(name, value)
            else -> throw IllegalArgumentException("member $name is a ${value.javaClass.simpleName}")
        }
        return mapper.writeValueAsBytes(node)
    }

    /** The JSON text of [value], quoted and escaped. */
    fun quoted(value: String): String = mapper.writeValueAsString(value)
}
