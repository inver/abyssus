/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.dto

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import net.nevinsky.abyssus.assets.SPLAT_FIELDS
import net.nevinsky.abyssus.assets.files.Asset
import net.nevinsky.abyssus.assets.files.MetaBase
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.json.obj
import net.nevinsky.abyssus.assets.json.opt
import net.nevinsky.abyssus.assets.json.text
import java.io.File

/** Lists a project's asset folders through the VFS, as the Abyssus tree shows them, parsing `meta.json` with [json]. */
class ProjectAssetListing(private val json: JsonProcessor) {
    /**
     * One [Asset] per folder under the `assets` next to [abss]. A folder whose `meta.json` is missing, malformed or of a
     * type this plugin does not know is still listed, as [MetaType.UNKNOWN] with no references.
     */
    fun list(abss: VirtualFile): List<Asset<Any>> = ProjectLayout.assetFolders(abss).map { dir ->
        runCatchingKeepingCancellation {
            val text = dir.findChild(ProjectLayout.META_FILE)?.readText() ?: return@runCatchingKeepingCancellation null

            @Suppress("UNCHECKED_CAST")
            val parsedMeta = json.parse(text, MetaBase::class.java) as MetaBase<Any>
            val references = runCatchingKeepingCancellation { references(json.readObject(text)) }.getOrDefault(emptyList())
            Asset(dir.name, parsedMeta, File(dir.path), references)
        }.getOrNull() ?: Asset(dir.name, MetaBase(0, 0L, MetaType.UNKNOWN, Any()), File(dir.path))
    }

    /**
     * The `uuid`s a `meta.json` holds in the fields Mundus resolves to other assets: the splat textures and the
     * `materials` list. Files named in `meta.json` live in the asset's own folder and are not references.
     */
    private fun references(meta: JsonNode): List<String> {
        val additional = meta.obj("additional") ?: return emptyList()
        val materials = additional.opt("materials")?.takeIf { it.isArray }
            ?.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }.orEmpty()
        return SPLAT_FIELDS.mapNotNull { additional.text(it) } + materials
    }
}
