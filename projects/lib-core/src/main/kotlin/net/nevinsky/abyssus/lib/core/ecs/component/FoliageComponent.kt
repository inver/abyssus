/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs.component

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.io.SourceBackedComponent

/**
 * Binds the foliage asset of a terrain entity: [assetName] is the name of a `FOLIAGE` asset folder, or null for none.
 * Only a string (or null) binds; any other value fails the binding, so the loader keeps the whole component raw.
 *
 * The component also keeps the object it was read from ([adoptSource]), so [sourceTree] writes every member it does not
 * model, in its order and with its number text, and changes only `assetName`, and only when [assetName] no longer
 * equals what was read. The name is not checked against the project here: that is the asset read's job.
 */
class FoliageComponent : SourceBackedComponent {
    @JsonDeserialize(using = AssetNameDeserializer::class)
    var assetName: String? = null

    private var source: ObjectNode? = null
    private var boundName: String? = null

    override fun adoptSource(node: JsonNode) {
        source = node as? ObjectNode
        boundName = assetName
    }

    override fun sourceTree(): JsonNode {
        val out = source?.deepCopy() ?: JsonNodeFactory.instance.objectNode()
        if (source == null || assetName != boundName) {
            assetName?.let { out.put("assetName", it) } ?: out.remove("assetName")
        }
        return out
    }
}

private class AssetNameDeserializer : StdDeserializer<String?>(String::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String? = when (p.currentToken()) {
        JsonToken.VALUE_STRING -> p.text
        JsonToken.VALUE_NULL -> null
        else -> ctxt.handleUnexpectedToken(String::class.java, p) as String?
    }

    override fun getNullValue(ctxt: DeserializationContext?): String? = null
}
