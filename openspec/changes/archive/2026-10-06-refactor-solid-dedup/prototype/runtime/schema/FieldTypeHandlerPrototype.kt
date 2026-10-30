package net.nevinsky.abyssus.lib.runtime.schema

import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.runtime.ecs.number
import net.nevinsky.abyssus.lib.runtime.schema.SchemaJson.Decoded

/** Task 3.1 candidate; kept independent until its size gate is evaluated. */
internal interface FieldTypeHandlerPrototype {
    fun encode(value: Any): JsonNode
    fun decode(field: SchemaField, node: JsonNode): Decoded
    fun defaultFor(): Any = fromJava(null)
    fun fromJava(value: Any?): Any
    val parts: List<String>
}

internal class DecimalHandlerPrototype : FieldTypeHandlerPrototype {
    override val parts = emptyList<String>()
    override fun encode(value: Any): JsonNode = number(value as Float)
    override fun fromJava(value: Any?): Any = (value as? Float) ?: 0f
    override fun decode(field: SchemaField, node: JsonNode): Decoded =
        if (node.isNumber && node.floatValue().isFinite())
            prototypeLimit(field, node.floatValue().toDouble(), node.floatValue())
        else Decoded.Unusable("$node is not a decimal number")
}

internal class VectorHandlerPrototype : FieldTypeHandlerPrototype {
    override val parts = listOf("x", "y", "z")
    override fun encode(value: Any): JsonNode = (value as SchemaVector).let {
        JsonNodeFactory.instance.objectNode().set<ObjectNode>("x", number(it.x))
            .set<ObjectNode>("y", number(it.y)).set("z", number(it.z))
    }
    override fun fromJava(value: Any?): Any =
        (value as? Vector3)?.let { SchemaVector(it.x, it.y, it.z) } ?: SchemaVector(0f, 0f, 0f)
    override fun decode(field: SchemaField, node: JsonNode): Decoded {
        val defaults = (field.default as SchemaVector).let { listOf(it.x, it.y, it.z) }
        val axes = prototypeParts(node, parts, defaults) ?: return Decoded.Unusable("$node is not an {x, y, z} vector")
        for (i in axes.indices) {
            val limit = prototypeLimit(field, axes[i].toDouble(), axes[i])
            if (limit is Decoded.Unusable) return Decoded.Unusable("${parts[i]}: ${limit.reason}")
        }
        return Decoded.Value(SchemaVector(axes[0], axes[1], axes[2]))
    }
}

// Existing shared helpers can be moved without net line growth if the candidate is adopted.
private fun prototypeLimit(field: SchemaField, value: Double, decoded: Any): Decoded {
    val min = field.min
    val max = field.max
    return when {
        min != null && field.minExclusive && value <= min -> Decoded.Unusable("${text(value)} is not greater than the minimum ${text(min)}")
        min != null && value < min -> Decoded.Unusable("${text(value)} is below the minimum ${text(min)}")
        max != null && value > max -> Decoded.Unusable("${text(value)} is above the maximum ${text(max)}")
        else -> Decoded.Value(decoded)
    }
}

private fun prototypeParts(node: JsonNode, keys: List<String>, defaults: List<Float>): List<Float>? {
    if (!node.isObject) return null
    return keys.mapIndexed { i, key ->
        val part = node.get(key) ?: return@mapIndexed defaults[i]
        if (!part.isNumber || !part.floatValue().isFinite()) return null
        part.floatValue()
    }
}
