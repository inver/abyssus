/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime.project

import java.nio.file.Files
import java.nio.file.Path

const val PROJECT_EXTENSION = "abss"
const val SCENE_EXTENSION = "scene"
const val SCENES_DIR = "scenes"

data class ProjectInfo(val name: String, val scenes: List<Path>)

/** Mundus project layout, independent of the editor's virtual filesystem. */
class ProjectFolder(private val dir: Path) {
    fun abss(): Path? = files(dir, PROJECT_EXTENSION).firstOrNull()
    fun sceneFiles(): List<Path> = files(dir.resolve(SCENES_DIR), SCENE_EXTENSION)

    private fun files(folder: Path, extension: String): List<Path> {
        if (!Files.isDirectory(folder)) return emptyList()
        return Files.list(folder).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".$extension") }
                .sorted(compareBy { it.fileName.toString() }).toList()
        }
    }
}
