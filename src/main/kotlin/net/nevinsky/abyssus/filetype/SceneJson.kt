/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.filetype

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.*
import java.math.BigDecimal
import java.math.BigInteger
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.assets.json.JsonFormat

/**
 * A floating-point literal that remembers its source text, so `2.50`, `-0.0` and `1.0E-4` are written back exactly
 * as they were read. [DoubleNode] alone would normalize them, and reading a scene must never change its numbers.
 */
private class RawNumberNode(val text: String) : NumericNode() {
    private val number = DoubleNode(text.toDouble())

    override fun asToken() = JsonToken.VALUE_NUMBER_FLOAT
    override fun numberType() = JsonParser.NumberType.DOUBLE
    override fun numberValue(): Number = number.numberValue()
    override fun intValue() = number.intValue()
    override fun longValue() = number.longValue()
    override fun floatValue() = number.floatValue()
    override fun doubleValue() = number.doubleValue()
    override fun decimalValue(): BigDecimal = number.decimalValue()
    override fun bigIntegerValue(): BigInteger = number.bigIntegerValue()
    override fun canConvertToInt() = number.canConvertToInt()
    override fun canConvertToLong() = number.canConvertToLong()
    override fun isFloatingPointNumber() = true
    override fun isDouble() = true
    override fun asText(): String = text
    override fun serialize(g: JsonGenerator, provider: SerializerProvider) = g.writeNumber(text)
    override fun equals(other: Any?) = other is RawNumberNode && other.text == text
    override fun hashCode() = text.hashCode()
}

/**
 * Reading and writing of scene JSON, and the one [mapper] that binds asset files to their DTOs. Nulls are kept (a
 * scene's `"skyboxName": null` is data), key order and number text are unchanged, and HTML characters are not escaped.
 */
object SceneJson {
    /** Unknown fields are ignored and properties keep declaration order, so a DTO lists its fields as the file does. */
    private val format = JsonFormat()

    val mapper: JsonMapper = format.mapperBuilder().build()

    private val nodes = JsonNodeFactory.instance

    private val prettyPrinter = format.prettyPrinter

    /** Parses [text] as exactly one JSON document; throws on malformed, empty or trailing input. */
    fun parse(text: String): JsonNode = mapper.createParser(text).use { p ->
        val root = read(p, p.nextToken() ?: throw IllegalArgumentException("empty JSON document"))
        if (p.nextToken() != null) throw IllegalArgumentException("unexpected content after the JSON document")
        root
    }

    fun parseObject(text: String): ObjectNode =
        parse(text) as? ObjectNode ?: throw IllegalArgumentException("expected a JSON object")

    /** Binds an already parsed [node] to [type]; throws when a field has the wrong shape. */
    fun <T> bind(node: JsonNode, type: Class<T>): T = mapper.treeToValue(node, type)

    fun pretty(node: JsonNode): String = mapper.writer(prettyPrinter).writeValueAsString(node) + "\n"

    fun compact(node: JsonNode): String = mapper.writeValueAsString(node)

    /** [node] in the style of [original]: indented when the original spans several lines, else on one line. */
    fun inStyleOf(original: String, node: JsonNode): String =
        if (original.contains('\n')) pretty(node) else compact(node)

    /** The scene as indented JSON; null when [text] is not a JSON object (so a half-edited or foreign file is never rewritten). */
    fun pretty(text: String): String? = runCatchingKeepingCancellation {
        val node = parse(text)
        if (!node.isObject) return null
        pretty(node)
    }.getOrNull()

    private fun read(p: JsonParser, token: JsonToken): JsonNode = when (token) {
        JsonToken.START_OBJECT -> nodes.objectNode().also { o ->
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                val name = p.currentName()
                o.set<JsonNode>(name, read(p, p.nextToken()))
            }
        }

        JsonToken.START_ARRAY -> nodes.arrayNode().also { a ->
            var next = p.nextToken()
            while (next != JsonToken.END_ARRAY) {
                a.add(read(p, next ?: throw IllegalArgumentException("unterminated array")))
                next = p.nextToken()
            }
        }

        JsonToken.VALUE_STRING -> TextNode.valueOf(p.text)
        JsonToken.VALUE_NUMBER_INT -> when (p.numberType) {
            JsonParser.NumberType.INT -> IntNode.valueOf(p.intValue)
            JsonParser.NumberType.LONG -> LongNode.valueOf(p.longValue)
            else -> BigIntegerNode.valueOf(p.bigIntegerValue)
        }

        JsonToken.VALUE_NUMBER_FLOAT -> RawNumberNode(p.text)
        JsonToken.VALUE_TRUE -> BooleanNode.TRUE
        JsonToken.VALUE_FALSE -> BooleanNode.FALSE
        JsonToken.VALUE_NULL -> NullNode.instance
        else -> throw IllegalArgumentException("unexpected JSON token $token")
    }
}
