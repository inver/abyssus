/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

/**
 * Binds the foliage asset of a terrain entity: [assetName] is the name of a `FOLIAGE` asset folder, or null for none.
 * Only a string (or null) binds; any other value fails the binding, so the loader keeps the whole component raw.
 *
 * Members the component does not model are kept in [extras] (in file order) and written back after `assetName`. The name
 * is not checked against the project here: that is the asset read's job.
 */
class FoliageComponent : Component {
    @JsonDeserialize(using = AssetNameDeserializer::class)
    var assetName: String? = null

    @field:JsonAnySetter
    private val unmodeled = LinkedHashMap<String, JsonNode>()

    @get:JsonAnyGetter
    val extras: Map<String, JsonNode> get() = unmodeled
}

private class AssetNameDeserializer : StdDeserializer<String?>(String::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String? = when (p.currentToken()) {
        JsonToken.VALUE_STRING -> p.text
        JsonToken.VALUE_NULL -> null
        else -> ctxt.handleUnexpectedToken(String::class.java, p) as String?
    }

    override fun getNullValue(ctxt: DeserializationContext?): String? = null
}
