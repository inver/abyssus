/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.assets.json

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * Binds Mundus JSON (`.abss`, `.scene`, asset `meta.json`) to Kotlin classes: unknown properties are skipped, unknown
 * enum values take their default, and properties keep declaration order. Create one and pass it to what needs it.
 */
class JsonProcessor {
    private val mapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .build()

    private val prettyPrinter: DefaultPrettyPrinter = DefaultPrettyPrinter(
        Separators.createDefaultInstance()
            .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
            .withObjectEmptySeparator("")
            .withArrayEmptySeparator(""),
    ).also {
        val indenter = DefaultIndenter("  ", "\n")
        it.indentObjectsWith(indenter)
        it.indentArraysWith(indenter)
    }

    /** [text] as a JSON tree; throws when it is not a JSON object. */
    fun readObject(text: String): JsonNode =
        mapper.readTree(text)?.takeIf { it.isObject } ?: throw IllegalArgumentException("expected a JSON object")

    fun <T> parse(text: String, clazz: Class<T>): T {
        return mapper.readValue(text, clazz)
    }

    fun pretty(obj: Any): String = mapper.writer(prettyPrinter).writeValueAsString(obj) + "\n"
}
