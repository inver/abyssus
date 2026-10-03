/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.scene.SceneDto
import net.nevinsky.abyssus.assets.files.Asset

/** A `.abss` project as the view shows it. [scenes] holds [SceneDto]s, and a [SceneError] for each that failed to read. */
data class ProjectDto(
    val name: String?,
    val scenes: List<Any>,
    val assets: List<Asset<Any>>
) {
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    constructor(@JsonProperty("name") name: String?) : this(name, emptyList(), emptyList())
}

/** A scene file that could not be read; shown as an `error` row under the scene's file name. */
data class SceneError(@get:JsonIgnore val file: VirtualFile, val error: String?)
