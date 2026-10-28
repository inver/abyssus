/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.io

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * A read view of one JSON object for callers that hold no Jackson tree types: the members it asks for come out as plain
 * Kotlin values, null when missing or of another kind. Reading JSON text into values is `lib-core`'s job; writing it is
 * `lib-core-editor`'s.
 */
class JsonObjectView internal constructor(private val node: ObjectNode) {
    /** The whole number member [name], or null. */
    fun int(name: String): Int? = node.get(name)?.takeIf { it.isIntegralNumber }?.intValue()

    /** The text member [name], or null. */
    fun text(name: String): String? = node.get(name)?.takeIf { it.isTextual }?.textValue()

    /** The member [name] as text whatever its kind (numbers and booleans as written), or null when missing. */
    fun asText(name: String): String? = node.get(name)?.asText()

    /** The member [name] as text whatever its kind; an [IllegalArgumentException] when missing. */
    fun requireText(name: String): String = asText(name) ?: throw IllegalArgumentException("missing $name")

    /** The member [name] as a whole number; an [IllegalArgumentException] when missing. */
    fun requireInt(name: String): Int = node.get(name)?.asInt() ?: throw IllegalArgumentException("missing $name")

    /** The member [name] as a whole number, or [default] when missing. */
    fun intOr(name: String, default: Int): Int = node.get(name)?.asInt() ?: default

    /** The list member [name] of text entries; null when it is not a list, and an [IllegalArgumentException] for a non-text entry. */
    fun strings(name: String): List<String>? = (node.get(name) as? ArrayNode)?.map {
        it.takeIf { e -> e.isTextual }?.textValue() ?: throw IllegalArgumentException("$name holds a non-text entry")
    }

    /** Every member as text, in file order. */
    fun entries(): Map<String, String> = node.properties().associate { (k, v) -> k to v.asText() }
}

/** Reads JSON text into [JsonObjectView]s. */
class JsonObjectReader(private val mapper: ObjectMapper = ObjectMapper()) {
    /** The object [text] holds; null when it is not a JSON object. */
    fun parse(text: String): JsonObjectView? = (mapper.readTree(text) as? ObjectNode)?.let(::JsonObjectView)

    /** The object [bytes] hold; null when they are not a JSON object. */
    fun parse(bytes: ByteArray): JsonObjectView? = (mapper.readTree(bytes) as? ObjectNode)?.let(::JsonObjectView)
}
