/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.project

import com.fasterxml.jackson.annotation.JsonIgnore
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.PROJECT_EXTENSION
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.SCENES_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.SCENE_EXTENSION
import net.nevinsky.abyssus.lib.core.scene.Scene
import java.nio.file.Files
import java.nio.file.Path

/**
 * A native project: its [name] and [scenes] as the `.abss` file holds them. A project made from a folder ([dir]; not
 * part of the file) also knows the files of its layout, independent of the editor's virtual filesystem: names are
 * case-sensitive (`.abss`, `.scene`), and a folder with no `.abss` is not a project. A project that is not made from
 * a folder has no files.
 */
data class Project(
    val name: String? = null,
    val scenes: List<Scene> = emptyList(),
    @get:JsonIgnore val dir: Path? = null,
) {
    /** The project file: the first `.abss` of [dir] by name; null when there is none. */
    fun file(): Path? = dir?.let { files(it, PROJECT_EXTENSION).firstOrNull() }

    /** The scene files of the `scenes` folder of [dir], sorted by name. */
    fun sceneFiles(): List<Path> = dir?.let { files(it.resolve(SCENES_DIR), SCENE_EXTENSION) }.orEmpty()

    private fun files(folder: Path, extension: String): List<Path> {
        if (!Files.isDirectory(folder)) {
            return emptyList()
        }
        return Files.list(folder).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".$extension") }
                .sorted(compareBy { it.fileName.toString() }).toList()
        }
    }
}
