/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.AssetField
import net.nevinsky.abyssus.EditError
import net.nevinsky.abyssus.FieldKind
import net.nevinsky.abyssus.FieldValue
import net.nevinsky.abyssus.assets.MetaType
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

/** The localized reason for a refused edit. */
fun editErrorMessage(error: EditError): String = AbyssusBundle.message("assetEditError.${error.name}")

/** What a choice reads as in a chooser: its label, `None` for no value, or the stored value marked as not found. */
fun choiceLabel(choice: AssetChoice): String = when {
    choice.value == null -> AbyssusBundle.message("assetNoneChoice")
    !choice.resolved -> AbyssusBundle.message("assetUnresolvedChoice", choice.label)
    else -> choice.label
}

/**
 * The editable properties of the asset in [folder] for a `meta.json` of [type], with their current values from [json] and,
 * for references, the choices found on disk. Empty for a type without editors. Reads files, so off the EDT.
 */
fun readFieldStates(folder: VirtualFile, type: MetaType, json: JsonNode, services: PanelServices): List<AssetFieldState> {
    val fields = services.assetFields.fields(type)
    if (fields.isEmpty()) return emptyList()
    val choices = AssetReferenceChoices(services.json)
    val assetsDir = folder.parent?.path?.let(::File)
    return fields.map { field ->
        val value = services.assetEditor.current(json, field)
        val current = (value as? FieldValue.Text)?.value
        val offered = when (field.kind) {
            FieldKind.ASSET_REFERENCE -> assetsDir?.let { choices.textures(it, current) } ?: emptyList()
            FieldKind.LOCAL_FILE -> choices.faces(File(folder.path), current)
            else -> emptyList()
        }
        AssetFieldState(field, value, offered)
    }
}
