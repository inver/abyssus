/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import java.util.*

data class SceneDto(
    /** The scene's id as the file holds it: a UUID for a scene made now, a number in older files. */
    val id: String = UUID.randomUUID().toString(),
    val name: String? = null,
    val ambientLightEnabled: Boolean = false,
    val ambientLight: BaseLightDto? = null,
    val fogEnabled: Boolean = false,
    val fog: FogDto? = null,
    val skyboxEnabled: Boolean = false,
    val skyboxName: String? = null,
    //todo replace this ugly JsonNode to correct dtos
    /** Raw native extension block; the editor displays its water surfaces without renaming the file key. */
    @param:JsonProperty("abyssus")
    @get:JsonProperty("abyssus")
    val water: JsonNode? = null,

    val ecs: Map<Long, Entity> = emptyMap()
)