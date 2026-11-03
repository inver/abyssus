/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * A plain Jackson tree reader for the UI tests, which look at the files an IDE session wrote. The product no longer has a tree
 * model (documents are DTOs); these tests only inspect and prepare text, so they keep a tree of their own.
 */
class SceneJson {
    // decimals are kept exactly, so `1.000` and `1.0` stay different numbers and print as they were read
    private val exact = ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
    private val mapper = exact.copy().enable(SerializationFeature.INDENT_OUTPUT)

    fun parse(text: String): JsonNode = exact.readTree(text)

    fun parseObject(text: String): ObjectNode = exact.readTree(text) as ObjectNode

    fun pretty(node: JsonNode): String = mapper.writeValueAsString(node) + "\n"

    fun pretty(text: String): String = pretty(parse(text))

    fun compact(node: JsonNode): String = exact.writeValueAsString(node)
}
