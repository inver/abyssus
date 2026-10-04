/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import net.nevinsky.abyssus.runtime.ecs.scene.number
import java.math.BigDecimal

/**
 * The one encoding of game component values in a scene file, given only the [ComponentSchema]: the game's
 * [ReflectiveCodec] and the editor both read and write through it. Values are as [SchemaField.default] describes.
 */
class SchemaJson {
    private val nodes = JsonNodeFactory.instance

    /**
     * The values of [node] for every field of [schema]: a missing field takes its default, and so does an unusable
     * one, which is reported to [problems] as `field <name>: <why>; the default <d> is used`.
     */
    fun decode(schema: ComponentSchema, node: JsonNode, problems: (String) -> Unit = {}): MutableMap<String, Any> {
        val values = LinkedHashMap<String, Any>()
        for (field in schema.fields) {
            val value = node.get(field.name)?.takeIf { !it.isNull }
            values[field.name] = if (value == null) field.default else when (val read = decodeValue(field, value)) {
                is Decoded.Value -> read.value
                is Decoded.Unusable -> field.default.also {
                    problems("field ${field.name}: ${read.reason}; the default ${encodeValue(field, field.default)} is used")
                }
            }
        }
        return values
    }

    /** An object of the [values] that differ from their field's default, in schema order; `{}` when none do. */
    fun encode(schema: ComponentSchema, values: Map<String, Any>): ObjectNode {
        val out = nodes.objectNode()
        for (field in schema.fields) {
            val value = values[field.name] ?: continue
            if (value != field.default) out.set<JsonNode>(field.name, encodeValue(field, value))
        }
        return out
    }

    /** [value] of [field] as the file holds it; decimals through [number], so `25.0` is written `25`. */
    fun encodeValue(field: SchemaField, value: Any): JsonNode = when (field.type) {
        FieldType.DECIMAL -> number(value as Float)
        FieldType.WHOLE, FieldType.ENTITY -> IntNode.valueOf(value as Int)
        FieldType.BOOLEAN -> BooleanNode.valueOf(value as Boolean)
        FieldType.TEXT, FieldType.CHOICE, FieldType.ASSET -> TextNode.valueOf(value as String)
        FieldType.VECTOR -> (value as SchemaVector).let {
            nodes.objectNode().set<ObjectNode>("x", number(it.x)).set<ObjectNode>("y", number(it.y)).set("z", number(it.z))
        }
        FieldType.COLOR -> (value as SchemaColor).let {
            nodes.objectNode().set<ObjectNode>("r", number(it.r)).set<ObjectNode>("g", number(it.g))
                .set<ObjectNode>("b", number(it.b)).set("a", number(it.a))
        }
    }

    /** What [node] holds for [field]: its value, or why it cannot be used (wrong type, not a choice, outside a limit). */
    fun decodeValue(field: SchemaField, node: JsonNode): Decoded = when (field.type) {
        FieldType.DECIMAL -> if (node.isNumber && node.floatValue().isFinite()) limited(field, node.floatValue().toDouble(), node.floatValue())
            else Decoded.Unusable("$node is not a decimal number")
        FieldType.WHOLE -> whole(node)?.let { limited(field, it.toDouble(), it) } ?: Decoded.Unusable("$node is not a whole number")
        FieldType.ENTITY -> whole(node)?.let { Decoded.Value(it) } ?: Decoded.Unusable("$node is not an entity id")
        FieldType.BOOLEAN -> if (node.isBoolean) Decoded.Value(node.booleanValue()) else Decoded.Unusable("$node is not true or false")
        FieldType.TEXT, FieldType.ASSET -> if (node.isTextual) Decoded.Value(node.textValue()) else Decoded.Unusable("$node is not text")
        FieldType.CHOICE -> when {
            !node.isTextual -> Decoded.Unusable("$node is not text")
            node.textValue() !in field.choices -> Decoded.Unusable("$node is not one of ${field.choices.joinToString(", ")}")
            else -> Decoded.Value(node.textValue())
        }
        FieldType.VECTOR -> parts(node, listOf("x", "y", "z"), (field.default as SchemaVector).let { listOf(it.x, it.y, it.z) })
            ?.let { Decoded.Value(SchemaVector(it[0], it[1], it[2])) } ?: Decoded.Unusable("$node is not an {x, y, z} vector")
        FieldType.COLOR -> parts(node, listOf("r", "g", "b", "a"), (field.default as SchemaColor).let { listOf(it.r, it.g, it.b, it.a) })
            ?.let { Decoded.Value(SchemaColor(it[0], it[1], it[2], it[3])) } ?: Decoded.Unusable("$node is not an {r, g, b, a} color")
    }

    /** The outcome of [decodeValue]. */
    sealed interface Decoded {
        data class Value(val value: Any) : Decoded
        data class Unusable(val reason: String) : Decoded
    }

    private fun whole(node: JsonNode): Int? = when {
        node.isIntegralNumber && node.canConvertToInt() -> node.intValue()
        node.isNumber && node.canConvertToExactIntegral() && node.canConvertToInt() -> node.intValue()
        else -> null
    }

    private fun limited(field: SchemaField, number: Double, value: Any): Decoded {
        val min = field.min
        val max = field.max
        return when {
            min != null && field.minExclusive && number <= min -> Decoded.Unusable("${text(number)} is not greater than the minimum ${text(min)}")
            min != null && number < min -> Decoded.Unusable("${text(number)} is below the minimum ${text(min)}")
            max != null && number > max -> Decoded.Unusable("${text(number)} is above the maximum ${text(max)}")
            else -> Decoded.Value(value)
        }
    }

    /** The numbers [keys] of [node], each missing one taken from [defaults]; null when [node] is not such an object. */
    private fun parts(node: JsonNode, keys: List<String>, defaults: List<Float>): List<Float>? {
        if (!node.isObject) return null
        return keys.mapIndexed { i, key ->
            val part = node.get(key) ?: return@mapIndexed defaults[i]
            if (!part.isNumber || !part.floatValue().isFinite()) return null
            part.floatValue()
        }
    }
}

/** A limit or value as short text: `5`, `0.5`. */
internal fun text(number: Double): String = BigDecimal(number.toString()).stripTrailingZeros().toPlainString()
