/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.scene

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

data class SceneDto(
    val id: Long? = null,
    val name: String? = null,
    val ambientLightEnabled: Boolean? = null,
    val ambientLight: BaseLightDto? = null,
    val fogEnabled: Boolean? = null,
    val fog: FogDto? = null,
    val skyboxEnabled: Boolean? = null,
    val skyboxName: String? = null,
    val ecs: JsonNode? = null,
    /**
     * The raw saved ray tracing preferences, decoded and validated by the plugin. Read only for Jackson: it is edited in
     * Properties, so property listings such as the Abyssus view tree do not show it as a row.
     */
    @param:JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val rayTracing: JsonNode? = null,
)
