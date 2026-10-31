/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.PROJECT_EXTENSION
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.SCENES_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.SCENE_EXTENSION
import java.nio.file.Files
import java.nio.file.Path

/**
 * A native project: its [name], [scenes] and settings as the `.abss` file holds them (unknown keys are not kept). A project made from a folder ([dir]; not
 * part of the file) also knows the files of its layout, independent of the editor's virtual filesystem: names are
 * case-sensitive (`.abss`, `.scene`), and a folder with no `.abss` is not a project. A project that is not made from
 * a folder has no files.
 */
data class ProjectDto(
    val name: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val scenes: List<SceneDto> = emptyList(),
    /** The registered UI asset folder names, in order; empty (and not written back) when the file omits them. */
    @param:JsonDeserialize(using = UiAssetNamesDeserializer::class)
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val uiAssetNames: List<String> = emptyList(),
    @get:JsonIgnore val dir: Path? = null,
    /** The format marker and version as the file holds them, so a rewrite keeps them (null: not written). */
    val format: String? = null,
    val formatVersion: Int? = null,
    /** The scene a project opens with; null when the file names none. */
    val activeSceneName: String? = null,
    val selectedCamera: Int? = null,
    /** The scene view's starting camera; null when the file has none. */
    val mainCamera: CameraDto? = null,
    /** Null when the file does not say; then the project does not use physics. */
    val physicsEnabled: Boolean? = null,
    /** The name of the editor's antialiasing level; the editor maps it, null when the file does not say. */
    val sceneViewAntialiasing: String? = null,
    /** The project's export settings as the file holds them; opaque to everything but export. */
    val settings: JsonNode? = null,
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
