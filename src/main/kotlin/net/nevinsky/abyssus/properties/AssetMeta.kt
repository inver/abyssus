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

package net.nevinsky.abyssus.properties

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.sceneview.textOf

sealed interface AssetMeta {
    /** The asset's `meta.json` as a table: [type] is the Meta `type` (null when absent or not text). */
    data class Loaded(val folder: VirtualFile, val type: String?, val rows: List<PropertyRow>, val json: JsonNode) : AssetMeta

    /** The `meta.json` is missing or is not a JSON object; [message] says why and is shown in place of the table. */
    data class Failed(val folder: VirtualFile, val message: String) : AssetMeta
}

/** Reads the `meta.json` of the asset [folder], preferring unsaved editor text over the file's content. */
fun loadAssetMeta(folder: VirtualFile): AssetMeta {
    val file = folder.takeIf { it.isValid }?.findChild(ProjectLayout.META_FILE)
        ?: return AssetMeta.Failed(folder, AbyssusBundle.message("propertiesMetaMissing", folder.name))
    return runCatchingKeepingCancellation { SceneJson.parseObject(textOf(file)) }.fold(
        onSuccess = { json -> AssetMeta.Loaded(folder, json.get("type")?.takeIf { it.isTextual }?.asText(), metaRowsOf(json), json) },
        onFailure = { AssetMeta.Failed(folder, AbyssusBundle.message("propertiesMetaParseError", it.message ?: it.javaClass.simpleName)) },
    )
}
