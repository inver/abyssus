/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.dto

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.dto.BaseLightDto
import net.nevinsky.abyssus.lib.core.dto.FogDto
import net.nevinsky.abyssus.lib.core.dto.RayTracingDto
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
    val rayTracing: RayTracingDto? = null,
    val rayTracingEnabled: Boolean = rayTracing != null,
    // parsing ecs graph should be done externally of deserializing scene
    val ecs: JsonNode? = null
)