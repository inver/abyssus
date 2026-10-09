/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import net.nevinsky.abyssus.lib.gdx.editor.document.AssetMetaReader
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_FIELDS
import net.nevinsky.abyssus.lib.gdx.assets.Asset
import net.nevinsky.abyssus.lib.gdx.assets.AssetMeta
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.util.obj
import net.nevinsky.abyssus.lib.core.util.opt
import net.nevinsky.abyssus.lib.core.util.text
import java.io.File
import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation

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
            val bound = document.typed(AssetMeta::class.java as Class<AssetMeta<Any>>) ?: return@runCatchingKeepingCancellation null
            // the name is the folder's, not a field of the file
            val parsedMeta = AssetMeta(dir.name, bound.formatVersion, bound.version, bound.lastModified, bound.type, bound.additional, bound.uuid)
            val references = runCatchingKeepingCancellation { references(document.json) }.getOrDefault(emptyList())
            Asset(File(dir.path), parsedMeta, references)
        }.getOrNull() ?: Asset(File(dir.path), AssetMeta(name = dir.name, type = MetaType.UNKNOWN, additional = Any()))
    }

    /**
     * The `uuid`s a `meta.json` holds in the fields Abyssus resolves to other assets: the splat textures, the
     * `materials` list and a procedural sky's `clouds`. Files named in `meta.json` live in the asset's own folder and
     * are not references.
     */
    private fun references(meta: JsonNode): List<String> {
        val additional = meta.obj("additional") ?: return emptyList()
        val materials = additional.opt("materials")?.takeIf { it.isArray }
            ?.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }.orEmpty()
        return SPLAT_FIELDS.mapNotNull { additional.text(it) } + materials + listOfNotNull(additional.text("clouds"))
    }
}
