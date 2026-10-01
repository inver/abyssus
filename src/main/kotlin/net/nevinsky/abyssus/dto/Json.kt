package net.nevinsky.abyssus.dto

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.node.BigIntegerNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import java.math.BigDecimal
import java.math.BigInteger

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
 * The one JSON entry point for reading asset files and for the two places that write them back. Keys keep their
 * order, nulls are kept, HTML characters are not escaped and numbers keep their text.
 */
object Json {
    private val mapper = ObjectMapper()
    private val nodes = JsonNodeFactory.instance

    private val prettyPrinter: DefaultPrettyPrinter = DefaultPrettyPrinter(
        Separators.createDefaultInstance()
            .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
            .withObjectEmptySeparator("")
            .withArrayEmptySeparator(""),
    ).also {
        val indenter = DefaultIndenter("  ", "\n")
        it.indentObjectsWith(indenter)
        it.indentArraysWith(indenter)
    }

    /** Parses [text] as exactly one JSON document; throws on malformed, empty or trailing input. */
    fun parse(text: String): JsonNode = mapper.createParser(text).use { p ->
        val root = read(p, p.nextToken() ?: throw IllegalArgumentException("empty JSON document"))
        if (p.nextToken() != null) throw IllegalArgumentException("unexpected content after the JSON document")
        root
    }

    fun parseObject(text: String): ObjectNode =
        parse(text) as? ObjectNode ?: throw IllegalArgumentException("expected a JSON object")

    fun write(node: JsonNode): String = mapper.writeValueAsString(node)

    fun writePretty(node: JsonNode): String = mapper.writer(prettyPrinter).writeValueAsString(node)

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

/** The child [name] unless it is absent or an explicit JSON null. */
fun JsonNode.opt(name: String): JsonNode? = get(name)?.takeIf { !it.isNull && !it.isMissingNode }
