/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import net.nevinsky.abyssus.editor.meta.AssetChoice
import net.nevinsky.abyssus.editor.meta.AssetReferenceChoices

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.EditError
import net.nevinsky.abyssus.core.assets.MetaType
import java.io.File

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
fun readFieldStates(folder: VirtualFile, type: MetaType, json: JsonNode, services: PanelServices): List<AssetFieldState> =
    assetFieldStates(File(folder.path), type, json, services.assetFields, services.assetEditor, AssetReferenceChoices(services.json))
