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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.scene.SceneDto
import net.nevinsky.abyssus.sceneview.Asset

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
