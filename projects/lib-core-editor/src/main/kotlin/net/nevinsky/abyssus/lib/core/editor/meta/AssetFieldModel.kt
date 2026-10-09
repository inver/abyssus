/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.meta

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.MetaType
import java.io.File

/**
 * One editable property of the shown asset: its [field], the [value] the file holds (or the default of an omitted key),
 * and for a texture or image property the [choices] it can take.
 */
data class AssetFieldState(val field: AssetField, val value: FieldValue, val choices: List<AssetChoice> = emptyList()) {
    val key: String get() = this.field.key
    val text: String get() = formatFieldValue(value)
}

/** [value] as an editor shows it; numbers keep Kotlin's shortest float text, a list is comma separated. */
fun formatFieldValue(value: FieldValue): String = when (value) {
    is FieldValue.Int -> value.value.toString()
    is FieldValue.Real -> value.value.toString()
    is FieldValue.Reals -> value.values.joinToString(", ")
    is FieldValue.Text -> value.value
    FieldValue.None -> ""
    is FieldValue.Invalid -> value.text
}

/**
 * The editable properties of the asset in [folder] for a `meta.json` of [type], with their current values from [json] and,
 * for references, the [choices] found on disk. Empty for a type without editors. Reads files.
 */
fun assetFieldStates(
    folder: File,
    type: MetaType,
    json: JsonNode,
    descriptions: AssetFieldDescriptions,
    editor: AssetMetaEditor,
    choices: AssetReferenceChoices,
): List<AssetFieldState> {
    val fields = descriptions.fields(type)
    if (fields.isEmpty()) return emptyList()
    val assetsDir = folder.parentFile
    return fields.map { field ->
        val value = editor.current(json, field)
        val current = (value as? FieldValue.Text)?.value
        val offered = when (field.kind) {
            FieldKind.ASSET_REFERENCE -> assetsDir?.let { choices.textures(it, current) } ?: emptyList()
            FieldKind.LOCAL_FILE -> choices.faces(folder, current)
            else -> emptyList()
        }
        AssetFieldState(field, value, offered)
    }
}

/** A row of an asset's details: a [PropertyRow] as `meta.json` lists it, or the editor of an [AssetFieldState]. */
sealed interface DetailRow {
    data class Plain(val row: PropertyRow) : DetailRow
    data class Field(val state: AssetFieldState) : DetailRow
}

/**
 * The details rows in file order, an `additional` row with an editable [fields] entry shown as its editor. When the
 * file has sections (a heading row), supported fields it omits follow with their effective value.
 */
fun detailRows(rows: List<PropertyRow>, fields: List<AssetFieldState>): List<DetailRow> {
    val editable = fields.associateBy { it.key }
    val shown = HashSet<String>()
    val result = rows.map { row ->
        val field = editable[row.name]?.takeIf { row.kind == RowKind.ADDITIONAL }
        if (field != null) shown += field.key
        if (field != null) DetailRow.Field(field) else DetailRow.Plain(row)
    }
    if (rows.none { it.kind == RowKind.HEADING }) return result
    return result + fields.filter { it.key !in shown }.map { DetailRow.Field(it) }
}
