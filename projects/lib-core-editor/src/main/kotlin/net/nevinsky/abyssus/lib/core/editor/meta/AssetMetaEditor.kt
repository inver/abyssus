/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.meta

import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_FIELDS
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.FloatNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.AtmosphereParams
import java.math.BigDecimal
import java.util.UUID
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation

/** How an editable `additional` field is shown and checked. */
enum class FieldKind {
    /** An integer above zero (terrain `size`). */
    POSITIVE_INT,

    /** A finite number above zero. */
    POSITIVE_FLOAT,

    /** A finite number of zero or more. */
    NON_NEGATIVE_FLOAT,

    /** A finite number strictly between -1 and 1. */
    UNIT_OPEN_FLOAT,

    /** Three finite numbers of zero or more. */
    NON_NEGATIVE_FLOAT3,

    /** The `uuid` of another asset, or none. */
    ASSET_REFERENCE,

    /** A file name inside the asset's own folder, or none. */
    LOCAL_FILE,
}

/** A value of an editable field. [Invalid] keeps the text of a stored value that does not fit the field's kind. */
sealed interface FieldValue {
    data class Int(val value: kotlin.Int) : FieldValue
    data class Real(val value: Float) : FieldValue
    data class Reals(val values: List<Float>) : FieldValue
    data class Text(val value: String) : FieldValue
    data object None : FieldValue
    data class Invalid(val text: String) : FieldValue
}

/** An editable key of the `additional` block, and the value a missing key stands for ([default], else none). */
class AssetField(val key: String, val kind: FieldKind, val default: FieldValue? = null) {
    /** The nullable kinds accept [FieldValue.None] as "clear". */
    val nullable: Boolean get() = kind == FieldKind.ASSET_REFERENCE || kind == FieldKind.LOCAL_FILE
}

/** Why an edit was refused; the plugin shows a localized message for each. */
enum class EditError {
    UNSUPPORTED_FORMAT,
    UNSUPPORTED_FIELD,
    NOT_AN_OBJECT,
    NOT_A_NUMBER,
    NOT_AN_INTEGER,
    NOT_POSITIVE,
    NEGATIVE,
    NOT_FINITE,
    MIE_G_RANGE,
    WRONG_COUNT,
    ATMOSPHERE_ORDER,
    NOT_A_UUID,
    BAD_FILE_NAME,
    EMPTY,
}

sealed interface ParseOutcome {
    data class Parsed(val value: FieldValue) : ParseOutcome
    data class Failed(val error: EditError) : ParseOutcome
}

sealed interface EditOutcome {
    /** The tree was changed. */
    data object Changed : EditOutcome

    /** The value already is the effective value: nothing was changed, and no default key was written. */
    data object NoChange : EditOutcome

    data class Rejected(val error: EditError) : EditOutcome

    /** The stored value differs from the one the editor was filled with; [actual] is what is stored now. */
    data class Conflict(val actual: FieldValue) : EditOutcome
}

/** The editable fields of each asset type. Everything else (identity, bookkeeping, file keys) stays read-only. */
class AssetFieldDescriptions {
    private val defaults = AtmosphereParams()

    private val terrain = listOf(
        AssetField("size", FieldKind.POSITIVE_INT),
        AssetField("uv", FieldKind.POSITIVE_FLOAT),
    ) + SPLAT_FIELDS.map { AssetField(it, FieldKind.ASSET_REFERENCE) }

    private val cube = listOf("top", "bottom", "left", "right", "front", "back").map { AssetField(it, FieldKind.LOCAL_FILE) }

    private val procedural = listOf(
        AssetField("planetRadius", FieldKind.POSITIVE_FLOAT, FieldValue.Real(defaults.planetRadius)),
        AssetField("atmosphereRadius", FieldKind.POSITIVE_FLOAT, FieldValue.Real(defaults.atmosphereRadius)),
        AssetField("betaRayleigh", FieldKind.NON_NEGATIVE_FLOAT3, FieldValue.Reals(defaults.betaRayleigh)),
        AssetField("betaMie", FieldKind.NON_NEGATIVE_FLOAT, FieldValue.Real(defaults.betaMie)),
        AssetField("heightRayleigh", FieldKind.POSITIVE_FLOAT, FieldValue.Real(defaults.heightRayleigh)),
        AssetField("heightMie", FieldKind.POSITIVE_FLOAT, FieldValue.Real(defaults.heightMie)),
        AssetField("mieG", FieldKind.UNIT_OPEN_FLOAT, FieldValue.Real(defaults.mieG)),
        AssetField("sunIntensity", FieldKind.NON_NEGATIVE_FLOAT, FieldValue.Real(defaults.sunIntensity)),
    )

    /** The editable fields of [type] in display order; empty for a type without editors. */
    fun fields(type: MetaType): List<AssetField> = when (type) {
        MetaType.TERRAIN -> terrain
        MetaType.SKYBOX -> cube
        MetaType.SKYBOX_PROCEDURAL -> procedural
        else -> emptyList()
    }

    fun field(type: MetaType, key: String): AssetField? = fields(type).firstOrNull { it.key == key }
}

/**
 * Reads and edits the editable `additional` fields of a `meta.json` tree. It works on any [JsonNode] tree, so the
 * caller picks a parser that keeps number text and key order; an edit changes exactly one key and nothing else, and
 * never touches `version`, `uuid`, `type`, `lastModified` or unknown keys.
 */
class AssetMetaEditor(private val descriptions: AssetFieldDescriptions, private val format: net.nevinsky.abyssus.lib.gdx.editor.document.AbyssusDocumentFormat = net.nevinsky.abyssus.lib.gdx.editor.document.AbyssusDocumentFormat()) {
    fun typeOf(root: JsonNode): MetaType =
        root.takeIf { format.validate(it, net.nevinsky.abyssus.lib.gdx.editor.document.DocumentKind.ASSET) == null }?.get("type")?.takeIf { it.isTextual }?.asText()?.let { name -> MetaType.entries.firstOrNull { it.name == name } }
            ?: MetaType.UNKNOWN

    /** The effective value of [field] in [root]: the stored value, the default of an omitted key, or none. */
    fun current(root: JsonNode, field: AssetField): FieldValue {
        val node = root.get("additional")?.get(field.key)
        if (node == null || node.isMissingNode) return field.default ?: FieldValue.None
        if (node.isNull) return FieldValue.None
        return read(field, node)
    }

    private fun read(field: AssetField, node: JsonNode): FieldValue = when (field.kind) {
        FieldKind.POSITIVE_INT ->
            if (node.isNumber && node.canConvertToInt() && node.doubleValue() == node.intValue().toDouble()) FieldValue.Int(node.intValue())
            else invalid(node)

        FieldKind.POSITIVE_FLOAT, FieldKind.NON_NEGATIVE_FLOAT, FieldKind.UNIT_OPEN_FLOAT ->
            if (node.isNumber) FieldValue.Real(node.floatValue()) else invalid(node)

        FieldKind.NON_NEGATIVE_FLOAT3 ->
            if (node.isArray && node.all { it.isNumber }) FieldValue.Reals(node.map { it.floatValue() }) else invalid(node)

        FieldKind.ASSET_REFERENCE, FieldKind.LOCAL_FILE ->
            if (node.isTextual) FieldValue.Text(node.asText()) else invalid(node)
    }

    private fun invalid(node: JsonNode) = FieldValue.Invalid(node.toString())

    /** Turns the text typed into an editor into a value of [field]'s kind, or says why it cannot be one. */
    fun parse(field: AssetField, text: String): ParseOutcome {
        val trimmed = text.trim()
        return when (field.kind) {
            FieldKind.POSITIVE_INT -> {
                val n = trimmed.toIntOrNull() ?: return failed(if (trimmed.toBigDecimalOrNull() == null) EditError.NOT_A_NUMBER else EditError.NOT_AN_INTEGER)
                parsed(FieldValue.Int(n))
            }

            FieldKind.POSITIVE_FLOAT, FieldKind.NON_NEGATIVE_FLOAT, FieldKind.UNIT_OPEN_FLOAT ->
                real(trimmed)?.let { parsed(FieldValue.Real(it)) } ?: failed(EditError.NOT_A_NUMBER)

            FieldKind.NON_NEGATIVE_FLOAT3 -> {
                val parts = trimmed.split(',', ' ', ';').filter { it.isNotBlank() }
                if (parts.size != 3) return failed(EditError.WRONG_COUNT)
                val values = parts.map { real(it) ?: return failed(EditError.NOT_A_NUMBER) }
                parsed(FieldValue.Reals(values))
            }

            FieldKind.ASSET_REFERENCE, FieldKind.LOCAL_FILE ->
                parsed(if (trimmed.isEmpty()) FieldValue.None else FieldValue.Text(trimmed))
        }
    }

    private fun real(text: String): Float? =
        text.toBigDecimalOrNull()?.let(BigDecimal::toFloat)?.takeIf { it.isFinite() }

    private fun parsed(value: FieldValue): ParseOutcome = ParseOutcome.Parsed(value)
    private fun failed(error: EditError): ParseOutcome = ParseOutcome.Failed(error)

    /** Why [value] is not acceptable for [field] given the rest of [root], or null when it is. */
    fun validate(root: JsonNode, field: AssetField, value: FieldValue): EditError? {
        val own = validateOwn(field, value)
        if (own != null) return own
        if (field.key == "planetRadius" || field.key == "atmosphereRadius") {
            val planet = if (field.key == "planetRadius") value else current(root, descriptions.field(MetaType.SKYBOX_PROCEDURAL, "planetRadius")!!)
            val atmosphere = if (field.key == "atmosphereRadius") value else current(root, descriptions.field(MetaType.SKYBOX_PROCEDURAL, "atmosphereRadius")!!)
            if (planet is FieldValue.Real && atmosphere is FieldValue.Real && atmosphere.value <= planet.value) return EditError.ATMOSPHERE_ORDER
        }
        return null
    }

    private fun validateOwn(field: AssetField, value: FieldValue): EditError? = when (field.kind) {
        FieldKind.POSITIVE_INT -> when {
            value !is FieldValue.Int -> EditError.NOT_AN_INTEGER
            value.value <= 0 -> EditError.NOT_POSITIVE
            else -> null
        }

        FieldKind.POSITIVE_FLOAT -> real(value) { if (it <= 0f) EditError.NOT_POSITIVE else null }
        FieldKind.NON_NEGATIVE_FLOAT -> real(value) { if (it < 0f) EditError.NEGATIVE else null }
        FieldKind.UNIT_OPEN_FLOAT -> real(value) { if (it <= -1f || it >= 1f) EditError.MIE_G_RANGE else null }
        FieldKind.NON_NEGATIVE_FLOAT3 -> when {
            value !is FieldValue.Reals -> EditError.WRONG_COUNT
            value.values.size != 3 -> EditError.WRONG_COUNT
            value.values.any { !it.isFinite() } -> EditError.NOT_FINITE
            value.values.any { it < 0f } -> EditError.NEGATIVE
            else -> null
        }

        FieldKind.ASSET_REFERENCE -> when (value) {
            FieldValue.None -> null
            is FieldValue.Text -> if (runCatchingKeepingCancellation { UUID.fromString(value.value) }.isSuccess) null else EditError.NOT_A_UUID
            else -> EditError.NOT_A_UUID
        }

        FieldKind.LOCAL_FILE -> when (value) {
            FieldValue.None -> null
            is FieldValue.Text -> if (value.value.isBlank()) EditError.EMPTY else if (isLocalName(value.value)) null else EditError.BAD_FILE_NAME
            else -> EditError.BAD_FILE_NAME
        }
    }

    private fun real(value: FieldValue, check: (Float) -> EditError?): EditError? = when {
        value !is FieldValue.Real -> EditError.NOT_A_NUMBER
        !value.value.isFinite() -> EditError.NOT_FINITE
        else -> check(value.value)
    }

    private fun isLocalName(name: String): Boolean =
        !name.startsWith("/") && !name.startsWith("\\") && !name.contains(':') &&
            name.split('/', '\\').none { it == ".." || it == "." || it.isEmpty() }

    /**
     * Sets [key] of `additional` to [value] if the stored value still is [expected], else reports a conflict. Nothing
     * is written for a refused edit, for a value equal to the effective value, or for a default that is already in
     * effect.
     */
    fun edit(root: JsonNode, key: String, expected: FieldValue, value: FieldValue): EditOutcome {
        if (format.validate(root, net.nevinsky.abyssus.lib.gdx.editor.document.DocumentKind.ASSET) != null) return EditOutcome.Rejected(EditError.UNSUPPORTED_FORMAT)
        val field = descriptions.field(typeOf(root), key) ?: return EditOutcome.Rejected(EditError.UNSUPPORTED_FIELD)
        val additional = root.get("additional") as? ObjectNode ?: return EditOutcome.Rejected(EditError.NOT_AN_OBJECT)
        val actual = current(root, field)
        if (actual != expected) return EditOutcome.Conflict(actual)
        validate(root, field, value)?.let { return EditOutcome.Rejected(it) }
        if (actual == value) return EditOutcome.NoChange
        additional.set<JsonNode>(key, node(value))
        return EditOutcome.Changed
    }

    private fun node(value: FieldValue): JsonNode = when (value) {
        is FieldValue.Int -> IntNode.valueOf(value.value)
        is FieldValue.Real -> FloatNode.valueOf(value.value)
        is FieldValue.Reals -> ArrayNode(JsonNodeFactory.instance).also { a -> value.values.forEach { a.add(FloatNode.valueOf(it)) } }
        is FieldValue.Text -> TextNode.valueOf(value.value)
        FieldValue.None -> NullNode.instance
        is FieldValue.Invalid -> throw IllegalArgumentException("an invalid value cannot be written")
    }
}
