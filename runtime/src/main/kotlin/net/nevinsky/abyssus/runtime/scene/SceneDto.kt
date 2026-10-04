/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.scene

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
)
