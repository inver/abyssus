/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import net.nevinsky.abyssus.lib.core.editor.meta.PropertyRow
import net.nevinsky.abyssus.lib.core.editor.meta.metaRowsOf
import net.nevinsky.abyssus.plugin.EditorBundle
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage

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
            document?.let { AssetMeta.Loaded(folder, it.type, metaRowsOf(it.json, EditorBundle), it.json) }
                ?: AssetMeta.Failed(folder, AbyssusBundle.message("propertiesMetaMissing", folder.name))
        },
        onFailure = {
            AssetMeta.Failed(
                folder,
                AbyssusBundle.message("propertiesMetaParseError", it.documentDisplayMessage())
            )
        },
    )
}
