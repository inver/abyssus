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

package net.nevinsky.abyssus.sceneview

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import net.nevinsky.abyssus.JsonProcessor
import net.nevinsky.abyssus.dto.*
import net.nevinsky.abyssus.filetype.SceneJson
import org.apache.commons.lang3.StringUtils
import java.io.File

/** The files of a terrain asset: [data] is the height data, [splat] the splat textures present (by `meta.json` field). */
data class TerrainFiles(val data: File, val size: Int, val uv: Float, val splat: Map<String, File>)


/**
 * Finds the files an asset folder under `<project>/assets` names in its `meta.json`. Pure file access, so it can run
 * on any thread; every lookup returns null for a missing folder, unreadable `meta.json` or missing file.
 */
@Service(Service.Level.PROJECT)
class ProjectAssetFiles private constructor(val projectDir: File, private val project: Project?) {
    /** The platform's project service. */
    constructor(project: Project) : this(File(project.projectFilePath), project)

    /** For code that has only the project folder. */
    constructor(projectDir: File) : this(projectDir, null)

    private val assetsDir = File(this.projectDir, ProjectLayout.ASSETS_DIR)

    /** Asset folders by the `uuid` in their `meta.json` (the first folder by name wins); read once, on first use. */
    private val foldersByUuid: Map<String, File> by lazy {
        val dirs = assetsDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()
        buildMap { for (dir in dirs) meta(dir)?.text("uuid")?.let { putIfAbsent(it, dir) } }
    }

    private fun folder(assetName: String): File? =
        // an asset name is a folder name: refuse anything that could leave the assets folder
        if (assetName.isEmpty() || assetName.contains('/') || assetName.contains('\\') || assetName == ".." || assetName == ".") null
        else File(assetsDir, assetName).takeIf { it.isDirectory }

    private fun meta(folder: File): JsonNode? = runCatchingKeepingCancellation {
        File(folder, ProjectLayout.META_FILE).takeIf { it.isFile }?.let { SceneJson.parseObject(it.readText()) }
    }.getOrNull()

    private fun additional(folder: File) = meta(folder)?.obj("additional")

    fun file(folder: File, name: String?): File? =
        name?.takeIf { it.isNotBlank() }?.let { File(folder, it) }
            ?.takeIf { it.isFile && it.canonicalPath.startsWith(folder.canonicalPath) }

    fun model(assetName: String): File? {
        val dir = folder(assetName) ?: return null
        return file(dir, additional(dir)?.text("file"))
    }

    fun terrain(assetName: String): TerrainFiles? {
        val dir = folder(assetName) ?: return null
        val a = additional(dir) ?: return null
        val data = file(dir, a.text("terrainFile")) ?: return null
        val size = a.float("size")?.toInt()?.takeIf { it > 0 } ?: return null
        val uv = a.float("uv") ?: 1f
        return TerrainFiles(data, size, uv, splatTextures(a))
    }

    /** `splat*` fields hold the `uuid` of a texture asset; each is resolved to that asset's image file when it exists. */
    private fun splatTextures(additional: JsonNode): Map<String, File> =
        ProjectLayout.SPLAT_FIELDS.mapNotNull { field ->
            val dir = additional.text(field)?.let(foldersByUuid::get) ?: return@mapNotNull null
            file(dir, additional(dir)?.text("file"))?.let { field to it }
        }.toMap()

    private fun <T, M : MetaBase<T>> loadMeta(clazz: Class<M>, folder: File): M? = runCatchingKeepingCancellation {
        File(folder, ProjectLayout.META_FILE)
            .takeIf { it.isFile }
            ?.let { service<JsonProcessor>().parse(it.readText(), clazz) }
    }.getOrNull()

    fun loadFile(assetName: String, fileName: String?): File? {
        if (StringUtils.isBlank(fileName)) return null
        val dir = folder(assetName) ?: return null
        return file(dir, fileName)
    }

    fun <T, M : MetaBase<T>> loadAsset(clazz: Class<M>, name: String): Asset<T>? {
        val dir = folder(name) ?: return null
        val meta = loadMeta(clazz, dir) ?: return null
        return Asset(name, meta, dir)
    }

    /** One [Asset] per folder under the `assets` next to [abss]; a folder without a readable `meta.json` is skipped. */
    fun loadShortAssets(abss: VirtualFile): List<Asset<Any>> {
        val service = service<JsonProcessor>()
        return ProjectLayout.assetFolders(abss).mapNotNull { dir ->
            runCatchingKeepingCancellation {
                val text = dir.findChild(ProjectLayout.META_FILE)?.readText() ?: return@runCatchingKeepingCancellation null

                @Suppress("UNCHECKED_CAST")
                val parsedMeta = service.parse(text, MetaBase::class.java) as MetaBase<Any>
                val references = runCatchingKeepingCancellation { references(SceneJson.parseObject(text)) }
                    .getOrDefault(emptyList())
                Asset(dir.name, parsedMeta, File(dir.path), references)
            }.getOrNull()
        }
    }

    /**
     * The `uuid`s a `meta.json` holds in the fields Mundus resolves to other assets: the splat textures and the
     * `materials` list. Files named in `meta.json` live in the asset's own folder and are not references.
     */
    private fun references(meta: JsonNode): List<String> {
        val additional = meta.obj("additional") ?: return emptyList()
        val materials = additional.opt("materials")?.takeIf { it.isArray }
            ?.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }.orEmpty()
        return ProjectLayout.SPLAT_FIELDS.mapNotNull { additional.text(it) } + materials
    }
}
