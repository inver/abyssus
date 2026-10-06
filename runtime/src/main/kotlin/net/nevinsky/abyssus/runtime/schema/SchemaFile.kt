/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import net.nevinsky.abyssus.core.io.JsonProcessor
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

/** Where a project keeps its exported component schema, relative to the project folder. */
const val SCHEMA_FILE = "abyssus/components.schema.json"

/** The supported schema file `version`. */
const val SCHEMA_VERSION = 1

/** A parsed schema file: the [components] it declares and a message for each [problems] it had. */
data class ParsedSchema(val components: List<ComponentSchema>, val problems: List<String>)

/**
 * Writes and reads the component schema file (`abyssus/components.schema.json`). Writing is stable: components in the
 * given order, fields in declaration order, two-space indent and LF line ends, so the same components give the same
 * bytes. Reading rejects an unknown `version` for the whole file and an unknown field type per component.
 */
class SchemaFile(private val json: JsonProcessor = JsonProcessor(), private val values: SchemaJson = SchemaJson()) {
    private val nodes = JsonNodeFactory.instance

    fun write(components: List<ComponentSchema>): String {
        val root = nodes.objectNode().put("version", SCHEMA_VERSION)
        val list = root.putArray("components")
        for (component in components) {
            val c = list.addObject().put("name", component.name).put("class", component.className).put("label", component.label)
            val fields = c.putArray("fields")
            for (field in component.fields) fields.add(fieldNode(field))
        }
        return StringBuilder().also { print(root, 0, it) }.append('\n').toString()
    }

    /** Writes [components] to `<projectDir>/abyssus/components.schema.json`, creating the folder; returns the file. */
    fun export(components: List<ComponentSchema>, projectDir: Path): Path {
        val file = projectDir.resolve(SCHEMA_FILE)
        Files.createDirectories(file.parent)
        Files.writeString(file, write(components))
        return file
    }

    /** The components [text] declares; a file of another version declares none. Throws when [text] is not JSON. */
    fun parse(text: String): ParsedSchema {
        val root = json.readObject(text)
        val version = root.get("version")
        if (version == null || !version.isIntegralNumber || version.intValue() != SCHEMA_VERSION)
            return ParsedSchema(emptyList(), listOf("unsupported version ${version ?: "(missing)"}; version $SCHEMA_VERSION is required"))
        val problems = ArrayList<String>()
        val components = root.get("components")?.takeIf { it.isArray }?.mapNotNull { node ->
            try {
                component(node)
            } catch (e: IllegalArgumentException) {
                problems += e.message.orEmpty()
                null
            }
        }.orEmpty()
        return ParsedSchema(components, problems)
    }

    private fun component(node: JsonNode): ComponentSchema {
        val name = node.get("name")?.takeIf { it.isTextual }?.textValue()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("a component has no name")
        val fields = node.get("fields")?.takeIf { it.isArray }?.map { field(name, it) }
            ?: throw IllegalArgumentException("component $name has no fields list")
        return ComponentSchema(name, node.get("class")?.asText().orEmpty(), node.get("label")?.asText()?.ifEmpty { null } ?: name.removeSuffix("Component"), fields)
    }

    private fun field(component: String, node: JsonNode): SchemaField {
        val name = node.get("name")?.takeIf { it.isTextual }?.textValue()
            ?: throw IllegalArgumentException("component $component has a field without a name")
        val typeId = node.get("type")?.asText()
        val type = FieldType.entries.firstOrNull { it.id == typeId }
            ?: throw IllegalArgumentException("component $component field $name has the unknown type $typeId")
        val choices = node.get("choices")?.map { it.asText() }.orEmpty()
        if (type == FieldType.CHOICE && choices.isEmpty()) throw IllegalArgumentException("component $component field $name has no choices")
        val shape = SchemaField(
            name, node.get("label")?.asText()?.ifEmpty { null } ?: name, type, fallback(type, choices),
            group = node.get("group")?.asText().orEmpty(),
            min = node.get("min")?.takeIf { it.isNumber }?.doubleValue(),
            max = node.get("max")?.takeIf { it.isNumber }?.doubleValue(),
            minExclusive = node.get("minExclusive")?.asBoolean() == true,
            choices = choices,
            assetType = node.get("assetType")?.asText(),
        )
        val default = node.get("default")?.let { values.decodeValue(shape.copy(min = null, max = null), it) }
        return when (default) {
            null -> shape
            is SchemaJson.Decoded.Value -> shape.copy(default = default.value)
            is SchemaJson.Decoded.Unusable -> throw IllegalArgumentException("component $component field $name has an unusable default: ${default.reason}")
        }
    }

    private fun fallback(type: FieldType, choices: List<String>): Any = if (type == FieldType.CHOICE) choices.first() else schemaValue(type, null)

    private fun fieldNode(field: SchemaField): ObjectNode {
        val out = nodes.objectNode().put("name", field.name).put("label", field.label).put("type", field.type.id)
        if (field.type == FieldType.CHOICE) out.putArray("choices").also { a -> field.choices.forEach(a::add) }
        field.assetType?.let { out.put("assetType", it) }
        out.set<JsonNode>("default", values.encodeValue(field, field.default))
        if (field.group.isNotEmpty()) out.put("group", field.group)
        field.min?.let { out.set<JsonNode>("min", limit(it)) }
        if (field.minExclusive) out.put("minExclusive", true)
        field.max?.let { out.set<JsonNode>("max", limit(it)) }
        return out
    }

    private fun limit(value: Double): JsonNode = BigDecimal(value.toString()).stripTrailingZeros().let {
        if (it.scale() <= 0) nodes.numberNode(it.toBigIntegerExact()) else DoubleNode.valueOf(value)
    }

    /** Objects and arrays of objects on their own lines; an array of plain values on one line. */
    private fun print(node: JsonNode, indent: Int, out: StringBuilder) {
        val pad = "  ".repeat(indent + 1)
        when {
            node.isObject -> {
                if (node.isEmpty) { out.append("{}"); return }
                out.append("{\n")
                node.properties().forEachIndexed { i, (key, value) ->
                    out.append(pad).append(TextNode.valueOf(key).toString()).append(": ")
                    print(value, indent + 1, out)
                    out.append(if (i < node.size() - 1) ",\n" else "\n")
                }
                out.append("  ".repeat(indent)).append('}')
            }
            node.isArray && node.all { it.isValueNode } -> out.append(node.joinToString(", ", "[", "]") { it.toString() })
            node.isArray -> {
                out.append("[\n")
                node.forEachIndexed { i, value ->
                    out.append(pad)
                    print(value, indent + 1, out)
                    out.append(if (i < node.size() - 1) ",\n" else "\n")
                }
                out.append("  ".repeat(indent)).append(']')
            }
            else -> out.append(node.toString())
        }
    }
}
