/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_FIELDS
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiMeta
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import net.nevinsky.abyssus.lib.core.util.obj
import net.nevinsky.abyssus.lib.core.util.opt
import net.nevinsky.abyssus.lib.core.util.text
import net.nevinsky.abyssus.lib.core.util.textsAt

/** Lists a project's asset folders through the VFS, as the Abyssus tree shows them, parsing `meta.json` with [json]. */
class ProjectAssetListing(json: JsonProcessor) {
    private val metaFiles = MetaFiles(AssetMetaReader(json))
    private val uiDecoder = UiCollectionDecoder()

    /**
     * One [Asset] per folder under the `assets` next to [abss]. A folder whose `meta.json` is missing, malformed or of
     * a type this plugin does not know is still listed, as [MetaType.UNKNOWN] with no references.
     */
    fun list(abss: VirtualFile): List<Asset<Any>> =
        ProjectLayout.assetFolders(abss).map { dir ->
            runCatchingKeepingCancellation {
                val document = metaFiles.inEditor(dir) ?: return@runCatchingKeepingCancellation null

                val bound = document.bound()
                // the name is the folder's, not a field of the file
                val additional: Any =
                    if (bound.type == MetaType.UI) {
                        UiMeta(document.ui())
                    } else {
                        bound.additional
                    }
                val parsedMeta =
                    AssetMeta(
                        bound.type,
                        dir.name,
                        bound.formatVersion,
                        bound.version,
                        bound.lastModified,
                        additional,
                        bound.uuid,
                    )
                val references = runCatchingKeepingCancellation { document.referenceUuids() }.getOrDefault(emptyList())
                Asset(File(dir.path), parsedMeta, references)
            }
                .getOrNull()
                ?: Asset(File(dir.path), AssetMeta(name = dir.name, type = MetaType.UNKNOWN, additional = Any()))
        }
}
