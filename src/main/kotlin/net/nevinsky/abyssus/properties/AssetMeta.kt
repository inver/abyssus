/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.core.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.core.AbyssusProjectLayout.META_FILE
import net.nevinsky.abyssus.dto.MetaFiles
import net.nevinsky.abyssus.filetype.documentDisplayMessage as displayMessage

sealed interface AssetMeta {
    /** The asset's `meta.json` as a table: [type] is the Meta `type` ([MetaType.UNKNOWN] when absent, not text or not known). */
    data class Loaded(val folder: VirtualFile, val type: MetaType, val rows: List<PropertyRow>, val json: JsonNode) :
        AssetMeta

    /** The `meta.json` is missing or is not a JSON object; [message] says why and is shown in place of the table. */
    data class Failed(val folder: VirtualFile, val message: String) : AssetMeta
}

/** Reads the `meta.json` of the asset [folder], preferring unsaved editor text over the file's content. */
fun loadAssetMeta(folder: VirtualFile, metaFiles: MetaFiles): AssetMeta {
    folder.takeIf { it.isValid }?.findChild(META_FILE)
        ?: return AssetMeta.Failed(folder, AbyssusBundle.message("propertiesMetaMissing", folder.name))
    return runCatchingKeepingCancellation { metaFiles.inEditor(folder) }.fold(
        onSuccess = { document ->
            document?.let { AssetMeta.Loaded(folder, it.type, metaRowsOf(it.json), it.json) }
                ?: AssetMeta.Failed(folder, AbyssusBundle.message("propertiesMetaMissing", folder.name))
        },
        onFailure = {
            AssetMeta.Failed(
                folder,
                AbyssusBundle.message("propertiesMetaParseError", it.displayMessage())
            )
        },
    )
}
