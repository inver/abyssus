/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.io

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.math.BigDecimal
import kotlin.math.abs
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

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
private interface OmitComponentDefaults

private class ComponentFloatSerializer : StdSerializer<Any>(Any::class.java) {
    override fun serialize(value: Any, gen: JsonGenerator, provider: SerializerProvider) {
        val v = (value as Number).toFloat().let { if (it.isFinite()) it else 0f }
        gen.writeTree(if (v == Math.rint(v.toDouble()).toFloat() && abs(v) < 1e9f) IntNode.valueOf(v.toInt())
            else DecimalNode(BigDecimal(v.toString())))
    }
}

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
    private val mapper: JsonMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .addModule(KotlinModule.Builder().build())
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        .addMixIn(Vector3::class.java, PublicFieldsOnly::class.java)
        .addMixIn(Quaternion::class.java, PublicFieldsOnly::class.java)
        .addMixIn(Color::class.java, PublicFieldsOnly::class.java)
        .build()

    private val componentReader = mapper.copy()
        .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
        .setDefaultMergeable(true)
        .reader(InjectableValues.Std().addValue(EcsReadWarnings::class.java.name, EcsReadWarnings(log)))

    private val componentValues = mapper.copy()
        .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
        .registerModule(SimpleModule()
            .addSerializer(java.lang.Float::class.java, ComponentFloatSerializer())
            .addSerializer(java.lang.Float.TYPE, ComponentFloatSerializer()))

    private val componentWriter = mapper.copy()
        .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
        .addMixIn(Component::class.java, OmitComponentDefaults::class.java)
        .registerModule(SimpleModule()
            .addSerializer(java.lang.Float::class.java, ComponentFloatSerializer())
            .addSerializer(java.lang.Float.TYPE, ComponentFloatSerializer()))

    /** Default-aware component binding without exposing the processor's mapper. */
    fun <C : Component> bindComponent(node: JsonNode, type: Class<C>): C = componentReader.forType(type).readValue(node)

    /** All modeled component values, including defaults, for comparing nested edits. */
    fun componentValues(component: Component): JsonNode = componentValues.valueToTree(component)

    /** Native component data with defaults omitted and decimal text matching scene writing. */
    fun componentTree(component: Component): JsonNode = componentWriter.valueToTree(component)

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
