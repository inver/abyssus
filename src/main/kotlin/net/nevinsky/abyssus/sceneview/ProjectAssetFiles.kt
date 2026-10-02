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
import net.nevinsky.abyssus.dto.Json
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.float
import net.nevinsky.abyssus.dto.obj
import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import net.nevinsky.abyssus.dto.text
import java.io.File

/** The files of a terrain asset: [data] is the height data, [splat] the splat textures present (by `meta.json` field). */
data class TerrainFiles(val data: File, val size: Int, val uv: Float, val splat: Map<String, File>)

/** The six faces of a skybox asset, in `meta.json` order. */
data class SkyboxFiles(val top: File, val bottom: File, val left: File, val right: File, val front: File, val back: File)

/**
 * Finds the files an asset folder under `<project>/assets` names in its `meta.json`. Pure file access, so it can run
 * on any thread; every lookup returns null for a missing folder, unreadable `meta.json` or missing file.
 */
class ProjectAssetFiles(projectDir: File) {
    /** Absolute: texture paths the model loader derives from the model file must not depend on the working directory. */
    val projectDir: File = projectDir.absoluteFile
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
        File(folder, ProjectLayout.META_FILE).takeIf { it.isFile }?.let { Json.parseObject(it.readText()) }
    }.getOrNull()

    private fun additional(folder: File) = meta(folder)?.obj("additional")

    private fun file(folder: File, name: String?): File? =
        name?.takeIf { it.isNotBlank() }?.let { File(folder, it) }?.takeIf { it.isFile && it.canonicalPath.startsWith(folder.canonicalPath) }

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

    fun skybox(assetName: String): SkyboxFiles? {
        val dir = folder(assetName) ?: return null
        val a = additional(dir) ?: return null
        fun face(name: String) = file(dir, a.text(name))
        return SkyboxFiles(
            face("top") ?: return null, face("bottom") ?: return null, face("left") ?: return null,
            face("right") ?: return null, face("front") ?: return null, face("back") ?: return null,
        )
    }
}
