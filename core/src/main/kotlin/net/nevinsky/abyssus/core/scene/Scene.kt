/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.scene

import com.fasterxml.jackson.databind.JsonNode
import java.util.*

data class Scene(
    /** The scene's id as the file holds it: a UUID for a scene made now, a number in older files. */
    val id: String = UUID.randomUUID().toString(),
    val name: String? = null,
    val ambientLightEnabled: Boolean = false,
    val ambientLight: BaseLight? = null,
    val fogEnabled: Boolean = false,
    val fog: Fog? = null,
    val skyboxEnabled: Boolean = false,
    val skyboxName: String? = null,
    val rayTracingEnabled: Boolean = false,
    val rayTracing: RayTracing? = null,
    //todo what we need to do with this?
    val ecs: JsonNode? = null,
)
