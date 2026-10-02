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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.scene.SceneDto

/**
 * One folder under a project's `assets`. `uuid` and `type` are bound from its `meta.json` and are all the view shows.
 * [name] is the folder, [references] the `uuid`s `meta.json` holds in the fields Mundus resolves to other assets (files
 * named in `meta.json` live in the asset's own folder and are not references) and [unused] marks an asset no scene
 * reaches; none of those three is a row.
 */
@JsonPropertyOrder("type", "uuid")
data class AssetInfo(
    @get:JsonIgnore val name: String = "",
    val uuid: String? = null,
    val type: String? = null,
    @get:JsonIgnore val references: List<String> = emptyList(),
    @get:JsonIgnore val unused: Boolean = false,
)

object ProjectAssets {
    /** Fields holding a list of asset `uuid`s (`ModelMeta.materials`). */
    private val REFERENCE_LISTS = listOf("materials")

    /** Changes when an asset folder is added, removed or renamed, or any `meta.json` changes. */
    fun stamp(abss: VirtualFile): Long =
        ProjectLayout.assetFolders(abss).fold(0L) { acc, dir ->
            (acc * 31 + dir.name.hashCode()) * 31 + (dir.findChild(ProjectLayout.META_FILE)?.modificationStamp ?: 0L)
        }

    fun read(abss: VirtualFile): List<AssetInfo> = ProjectLayout.assetFolders(abss).map { dir ->
        val meta = runCatchingKeepingCancellation { dir.findChild(ProjectLayout.META_FILE)?.let { SceneJson.parseObject(it.text()) } }.getOrNull()
        parse(dir.name, meta)
    }

    fun parse(name: String, meta: JsonNode?): AssetInfo {
        val additional = meta?.obj("additional")
        val references = additional?.let { a ->
            ProjectLayout.SPLAT_FIELDS.mapNotNull { a.text(it) } +
                REFERENCE_LISTS.flatMap { f -> a.opt(f)?.takeIf { it.isArray }?.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() } ?: emptyList() }
        } ?: emptyList()
        val bound = meta?.let { runCatchingKeepingCancellation { SceneJson.bind(it, AssetInfo::class.java) }.getOrNull() }
        return (bound ?: AssetInfo()).copy(name = name, references = references)
    }

    /**
     * What a scene names directly: `assetName` and `shaderKey` values anywhere in its ECS data, and `skyboxName`.
     * A name matching no asset folder (a bundled shader) is simply never found later.
     */
    fun sceneReferences(scene: SceneDto): Set<String> {
        val names = mutableSetOf<String>()
        scene.ecs?.let { ecs ->
            for (field in listOf("assetName", "shaderKey")) ecs.findValues(field).forEach { v -> if (v.isTextual) names += v.asText() }
        }
        scene.skyboxName?.let { names += it }
        return names
    }

    /** Folder names of the assets reachable from [roots] (folder names) through `uuid` references, transitively. */
    fun usedAssets(assets: List<AssetInfo>, roots: Set<String>): Set<String> {
        val byName = assets.associateBy { it.name }
        val byUuid = assets.filter { it.uuid != null }.associateBy { it.uuid!! }
        val used = mutableSetOf<String>()
        val pending = ArrayDeque<AssetInfo>()
        fun visit(asset: AssetInfo?) {
            if (asset != null && used.add(asset.name)) pending += asset
        }
        roots.forEach { visit(byName[it]) }
        while (pending.isNotEmpty()) pending.removeFirst().references.forEach { visit(byUuid[it]) }
        return used
    }
}
