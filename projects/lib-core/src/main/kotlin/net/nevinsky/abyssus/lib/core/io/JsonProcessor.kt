/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.io

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.Logger

/**
 * Mixed into libGDX's `Vector3`, `Quaternion` and `Color`, whose many overloaded `set...` methods Jackson would otherwise
 * read as conflicting setters: only their public `x`, `y`, `z`, `w` / `r`, `g`, `b`, `a` fields bind.
 */
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    creatorVisibility = JsonAutoDetect.Visibility.NONE,
)
private interface PublicFieldsOnly

/**
 * Binds native JSON (`.abss`, `.scene`, asset `meta.json`) to Kotlin classes: unknown properties are skipped, unknown
 * enum values take their default, and properties keep declaration order. Create one and pass it to what needs it.
 */
class JsonProcessor(internal val log: Logger) {
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

    /** The configured mapper, for code that binds JSON itself (`readValue`, readers with injected values). */
    val mapper: JsonMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .addModule(KotlinModule.Builder().build())
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        .addMixIn(Vector3::class.java, PublicFieldsOnly::class.java)
        .addMixIn(Quaternion::class.java, PublicFieldsOnly::class.java)
        .addMixIn(Color::class.java, PublicFieldsOnly::class.java)
        .build()

    /** [text] as a JSON tree; throws when it is not a JSON object. */
    fun readObject(text: String): JsonNode =
        mapper.readTree(text)?.takeIf { it.isObject } ?: throw IllegalArgumentException("expected a JSON object")

    fun ecsReader(): Pair<ObjectReader, EcsReadWarnings> {
        val warnings = EcsReadWarnings(log)
        val reader = mapper.reader(
            InjectableValues.Std().addValue(EcsReadWarnings::class.java.name, warnings),
        )
        return Pair(reader, warnings)
    }

    //todo add validation on parsing for formats
    fun <T> parse(text: String, clazz: Class<T>): T {
        return mapper.readValue(text, clazz)
    }

    /** Binds an already parsed [node] to [clazz], with the same rules as [parse]. */
    fun <T> bind(node: JsonNode, clazz: Class<T>): T = mapper.treeToValue(node, clazz)

    /** [obj] as one line of JSON, with no trailing newline. */
    fun toString(obj: Any): String = mapper.writeValueAsString(obj)

    fun pretty(obj: Any): String = mapper.writer(prettyPrinter).writeValueAsString(obj) + "\n"
}
