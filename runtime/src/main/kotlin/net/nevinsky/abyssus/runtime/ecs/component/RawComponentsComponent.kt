package net.nevinsky.abyssus.runtime.ecs.component

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode

//todo is we needed this?
class RawComponentsComponent(
    val archetype: JsonNode? = null,
    val components: MutableMap<String, JsonNode> = LinkedHashMap(),
    val order: MutableList<String> = ArrayList(),
) : Component
