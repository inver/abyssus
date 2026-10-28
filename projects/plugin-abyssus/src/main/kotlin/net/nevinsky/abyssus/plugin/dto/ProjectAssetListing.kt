/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_FIELDS
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiMeta
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import net.nevinsky.abyssus.lib.core.util.obj
import net.nevinsky.abyssus.lib.core.util.opt
import net.nevinsky.abyssus.lib.core.util.text
import net.nevinsky.abyssus.lib.core.util.textsAt
import java.io.File
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation

/** Lists a project's asset folders through the VFS, as the Abyssus tree shows them, parsing `meta.json` with [json]. */
class ProjectAssetListing(json: JsonProcessor) {
    private val metaFiles = MetaFiles(AssetMetaReader(json))
    private val uiDecoder = UiCollectionDecoder()

    /**
     * One [Asset] per folder under the `assets` next to [abss]. A folder whose `meta.json` is missing, malformed or of a
     * type this plugin does not know is still listed, as [MetaType.UNKNOWN] with no references.
     */
    fun list(abss: VirtualFile): List<Asset<Any>> = ProjectLayout.assetFolders(abss).map { dir ->
        runCatchingKeepingCancellation {
            val document = metaFiles.inEditor(dir) ?: return@runCatchingKeepingCancellation null

            @Suppress("UNCHECKED_CAST")
            val bound = document.typed(AssetMeta::class.java as Class<AssetMeta<Any>>) ?: return@runCatchingKeepingCancellation null
            // the name is the folder's, not a field of the file
            val additional: Any = if (bound.type == MetaType.UI) UiMeta(uiDecoder.decode(document.document)) else bound.additional
            val parsedMeta = AssetMeta(bound.type, dir.name, bound.formatVersion, bound.version, bound.lastModified, additional, bound.uuid)
            val references = runCatchingKeepingCancellation { references(document.document) }.getOrDefault(emptyList())
            Asset(File(dir.path), parsedMeta, references)
        }.getOrNull() ?: Asset(File(dir.path), AssetMeta(name = dir.name, type = MetaType.UNKNOWN, additional = Any()))
    }

    /**
     * The `uuid`s a `meta.json` holds in the fields Abyssus resolves to other assets: the splat textures, the
     * `materials` list and a procedural sky's `clouds`. Files named in `meta.json` live in the asset's own folder and
     * are not references.
     */
    private fun references(meta: Document): List<String> {
        if (!meta.isObjectAt("additional")) return emptyList()
        val materials = meta.textsAt("additional", "materials")
        return SPLAT_FIELDS.mapNotNull { meta.textAt("additional", it) } + materials + listOfNotNull(meta.textAt("additional", "clouds"))
    }
}
