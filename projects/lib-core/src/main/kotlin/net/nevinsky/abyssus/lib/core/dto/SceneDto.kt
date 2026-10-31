/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
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

    val ecs: MutableMap<Long, EntityDto> = mutableMapOf()
) {

    /** The entity with id [id], or null. */
    @JsonIgnore
    fun entity(id: Long): EntityDto? = ecs[id]

    /** The entities with their ids, in map order. */
    @JsonIgnore
    fun entities(): List<Pair<Long, EntityDto>> = ecs.entries.map { Pair(it.key, it.value) }

    /** The id a new entity takes: one more than the largest id, or 1 for a scene without entities. */
    @JsonIgnore
    fun nextId(): Long = (ecs.keys.maxOrNull() ?: 0L) + 1

    /**
     * The name of entity [id]: its `NameComponent.name` when that is non-blank text, else the id as text.
     * Null when the scene has no such entity.
     */
    @JsonIgnore
    fun nameOf(id: Long): String? {
        val entity = entity(id) ?: return null
        return entity.components.filterIsInstance<NameComponent>().firstOrNull()?.name
            ?.takeIf { it.isNotBlank() } ?: id.toString()
    }

    /** The first component of entity [id] that is an instance of [type], or null. */
    @JsonIgnore
    fun <C : Component> component(id: Long, type: Class<C>): C? =
        entity(id)?.components?.firstOrNull { type.isInstance(it) }?.let(type::cast)
}
