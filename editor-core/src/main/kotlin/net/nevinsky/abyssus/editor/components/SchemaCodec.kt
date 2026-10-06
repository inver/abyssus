/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.components

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.runtime.json.number
import net.nevinsky.abyssus.runtime.schema.ComponentSchema
import net.nevinsky.abyssus.runtime.schema.FieldType
import net.nevinsky.abyssus.runtime.schema.SchemaColor
import net.nevinsky.abyssus.runtime.schema.SchemaField
import net.nevinsky.abyssus.runtime.schema.SchemaJson
import net.nevinsky.abyssus.runtime.schema.SchemaVector

/** A schema-declared component as the editor holds it: its values by field name (see [SchemaField.default]). */
class SchemaValues(val values: MutableMap<String, Any>) : Component

/** Reads and writes a schema-declared component through [SchemaJson], with no game class. */
class SchemaCodec(val schema: ComponentSchema, private val json: SchemaJson) : ComponentCodec<SchemaValues> {
    override val name get() = schema.name
    override val type = SchemaValues::class.java
    override fun read(node: JsonNode) = SchemaValues(json.decode(schema, node))
    override fun write(component: SchemaValues): JsonNode = json.encode(schema, component.values)
}

/** A component decimal as the file writes it. */
internal fun decimalText(v: Float) = number(v).asText()

/**
 * The editor fields of a schema [field]: one per value, a vector or color as dotted decimals (`leadout.x`). A vector's
 * limits hold for each axis.
 */
private fun schemaFields(field: SchemaField): List<ComponentField<SchemaValues>> {
    fun <T : Any> one(kind: FieldKind, show: (T) -> String, parse: (String) -> Any, choices: List<String> = emptyList(), optional: Boolean = false) =
        ComponentField<SchemaValues>(
            field.name, kind, { @Suppress("UNCHECKED_CAST") show(it.values[field.name] as T) }, { c, t -> c.values[field.name] = parse(t) },
            choices, optional, field.label, field.group, field.min, field.max, field.minExclusive, field.assetType,
        )
    fun parts(keys: List<String>, read: (Any) -> List<Float>, limited: Boolean, make: (List<Float>) -> Any) = keys.mapIndexed { i, key ->
        ComponentField<SchemaValues>(
            "${field.name}.$key", FieldKind.FLOAT, { decimalText(read(it.values.getValue(field.name))[i]) },
            { c, t -> c.values[field.name] = make(read(c.values.getValue(field.name)).toMutableList().also { p -> p[i] = t.trim().toFloat() }) },
            label = "${field.label} $key", group = field.group,
            min = field.min.takeIf { limited }, max = field.max.takeIf { limited }, minExclusive = limited && field.minExclusive,
        )
    }
    return when (field.type) {
        FieldType.DECIMAL -> listOf(one<Float>(FieldKind.FLOAT, ::decimalText, { it.trim().toFloat() }))
        FieldType.WHOLE -> listOf(one<Int>(FieldKind.INT, Int::toString, { it.trim().toInt() }))
        FieldType.BOOLEAN -> listOf(one<Boolean>(FieldKind.BOOLEAN, Boolean::toString, { it.trim().toBooleanStrict() }))
        FieldType.TEXT -> listOf(one<String>(FieldKind.TEXT, { it }, { it }, optional = true))
        FieldType.CHOICE -> listOf(one<String>(FieldKind.CHOICE, { it }, { it.trim() }, field.choices))
        FieldType.ENTITY -> listOf(one<Int>(FieldKind.ENTITY_REF, Int::toString, { it.trim().toInt() }))
        FieldType.ASSET -> listOf(one<String>(FieldKind.ASSET_NAME, { it }, { it.trim() }, optional = true))
        FieldType.VECTOR -> parts(listOf("x", "y", "z"), { (it as SchemaVector).let { v -> listOf(v.x, v.y, v.z) } }, limited = true) { SchemaVector(it[0], it[1], it[2]) }
        FieldType.COLOR -> parts(listOf("r", "g", "b", "a"), { (it as SchemaColor).let { c -> listOf(c.r, c.g, c.b, c.a) } }, limited = false) { SchemaColor(it[0], it[1], it[2], it[3]) }
    }
}

/** The editor kind of a schema-declared component, with its defaults as a new one starts. */
internal fun schemaKind(schema: ComponentSchema, json: SchemaJson): ComponentKind<SchemaValues> =
    ComponentKind(schema.name, SchemaCodec(schema, json), schema.fields.flatMap(::schemaFields), {
        SchemaValues(schema.fields.associateTo(LinkedHashMap()) { it.name to it.default })
    }, schema.label)
