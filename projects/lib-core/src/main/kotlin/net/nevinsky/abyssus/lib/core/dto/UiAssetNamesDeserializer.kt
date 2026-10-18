/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.dto

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

/** A mixed project registry keeps only string entries; malformed or absent registries remain empty. */
class UiAssetNamesDeserializer : JsonDeserializer<List<String>>() {
    override fun getNullValue(ctxt: DeserializationContext): List<String> = emptyList()

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): List<String> {
        if (!parser.isExpectedStartArrayToken) {
            parser.skipChildren()
            return emptyList()
        }
        return buildList {
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken == JsonToken.VALUE_STRING) {
                    add(parser.text)
                } else parser.skipChildren()
            }
        }
    }
}
