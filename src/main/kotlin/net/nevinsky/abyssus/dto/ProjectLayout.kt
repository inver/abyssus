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

import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Where a Mundus project keeps its files: `<name>.abss` beside a `scenes` folder of `.scene` files and an `assets`
 * folder with one folder per asset, each described by its `meta.json`.
 */
object ProjectLayout {
    const val PROJECT_EXTENSION = "abss"
    const val SCENE_EXTENSION = "scene"
    const val SCENES_DIR = "scenes"
    const val ASSETS_DIR = "assets"
    const val META_FILE = "meta.json"

    /** Exact, case-sensitive, suffix-based match: `.SCENE` and `.scene.bak` are not asset files. */
    val ASSET_EXTENSIONS = setOf(SCENE_EXTENSION, PROJECT_EXTENSION)

    /** The terrain `meta.json` field naming the splat map texture asset (`TerrainMeta`). */
    const val SPLAT_MAP = "splatMap"

    /** The terrain `meta.json` fields naming the base and the four channel layer textures, in shader unit order. */
    val SPLAT_LAYERS = listOf("splatBase", "splatR", "splatG", "splatB", "splatA")

    val SPLAT_FIELDS = listOf(SPLAT_MAP) + SPLAT_LAYERS

    fun isAssetFile(file: VirtualFile) = !file.isDirectory && file.extension in ASSET_EXTENSIONS

    fun isScene(file: VirtualFile) = !file.isDirectory && file.extension == SCENE_EXTENSION

    /** The `.abss` of the project a scene belongs to: the one beside the scene's `scenes` folder. */
    fun abssFor(sceneFile: VirtualFile): VirtualFile? {
        val dir = sceneFile.parent?.takeIf { it.name == SCENES_DIR } ?: return null
        return dir.parent?.children?.firstOrNull { it.extension == PROJECT_EXTENSION }
    }

    /** A scene in the `scenes` folder next to an `.abss` is shown inside that project, not on its own. */
    fun isProjectScene(file: VirtualFile) = file.extension == SCENE_EXTENSION && abssFor(file) != null

    /** The folder holding a scene's `.abss` and `assets`, or null for a scene outside a project. */
    fun projectDirFor(sceneFile: VirtualFile): File? = abssFor(sceneFile)?.parent?.let { File(it.path) }

    /** The `.scene` files of the project [abss], by name. */
    fun sceneFiles(abss: VirtualFile): List<VirtualFile> =
        abss.parent?.findChild(SCENES_DIR)?.children?.filter(::isScene)?.sortedBy { it.name } ?: emptyList()

    fun assetFolders(abss: VirtualFile): List<VirtualFile> =
        abss.parent?.findChild(ASSETS_DIR)?.children?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
}
