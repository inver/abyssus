/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.dto

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.assets.SPLAT_FIELDS
import net.nevinsky.abyssus.assets.Asset
import net.nevinsky.abyssus.assets.AssetMeta
import net.nevinsky.abyssus.assets.MetaType
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.sceneview.obj
import net.nevinsky.abyssus.sceneview.opt
import net.nevinsky.abyssus.sceneview.text
import java.io.File
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation

/** Lists a project's asset folders through the VFS, as the Abyssus tree shows them, parsing `meta.json` with [json]. */
class ProjectAssetListing(json: JsonProcessor) {
    private val metaFiles = MetaFiles(AssetMetaReader(json))

    /**
     * One [Asset] per folder under the `assets` next to [abss]. A folder whose `meta.json` is missing, malformed or of a
     * type this plugin does not know is still listed, as [MetaType.UNKNOWN] with no references.
     */
    fun list(abss: VirtualFile): List<Asset<Any>> = ProjectLayout.assetFolders(abss).map { dir ->
        runCatchingKeepingCancellation {
            val document = metaFiles.inEditor(dir) ?: return@runCatchingKeepingCancellation null

            @Suppress("UNCHECKED_CAST")
            val parsedMeta = document.typed(AssetMeta::class.java as Class<AssetMeta<Any>>) ?: return@runCatchingKeepingCancellation null
            val references = runCatchingKeepingCancellation { references(document.json) }.getOrDefault(emptyList())
            Asset(dir.name, parsedMeta, File(dir.path), references)
        }.getOrNull() ?: Asset(dir.name, AssetMeta(0, 0L, MetaType.UNKNOWN, Any()), File(dir.path))
    }

    /**
     * The `uuid`s a `meta.json` holds in the fields Abyssus resolves to other assets: the splat textures and the
     * `materials` list. Files named in `meta.json` live in the asset's own folder and are not references.
     */
    private fun references(meta: JsonNode): List<String> {
        val additional = meta.obj("additional") ?: return emptyList()
        val materials = additional.opt("materials")?.takeIf { it.isArray }
            ?.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }.orEmpty()
        return SPLAT_FIELDS.mapNotNull { additional.text(it) } + materials
    }
}
