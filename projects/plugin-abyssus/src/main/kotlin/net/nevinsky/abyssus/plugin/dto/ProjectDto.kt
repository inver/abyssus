/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.dto.SceneDto

/** A `.abss` project as the view shows it. [scenes] holds [SceneEntry]s, and a [SceneError] for each that failed to read. */
data class ProjectDto(
    val name: String?,
    val scenes: List<Any>,
    val assets: List<Asset<Any>>,
)

/** A scene file that could not be read; shown as an `error` row under the scene's file name. */
data class SceneError(@get:JsonIgnore val file: VirtualFile, val error: String?)

/** Editor source paired with a platform-independent scene. */
data class SceneEntry(@get:JsonIgnore val file: VirtualFile, val scene: SceneDto)
