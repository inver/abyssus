/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.dto

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.assets.ASSETS_DIR
import java.io.File

/**
 * Where a native Abyssus project keeps its files: `<name>.abss` beside a `scenes` folder of `.scene` files and an `assets`
 * folder with one folder per asset, each described by its `meta.json`.
 */
object ProjectLayout {
    const val PROJECT_EXTENSION = net.nevinsky.abyssus.runtime.project.PROJECT_EXTENSION
    const val SCENE_EXTENSION = net.nevinsky.abyssus.runtime.project.SCENE_EXTENSION
    const val SCENES_DIR = net.nevinsky.abyssus.runtime.project.SCENES_DIR

    /** Exact, case-sensitive, suffix-based match: `.SCENE` and `.scene.bak` are not asset files. */
    val ASSET_EXTENSIONS = setOf(SCENE_EXTENSION, PROJECT_EXTENSION)

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
