/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.io

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.Logger
import java.math.BigDecimal
import kotlin.math.abs

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
        gen.writeTree(
            if (v == Math.rint(v.toDouble()).toFloat() && abs(v) < 1e9f) IntNode.valueOf(v.toInt())
            else DecimalNode(BigDecimal(v.toString()))
        )
    }
}

/**
 * A tree parser that also hands out the node it is walking, so a deserializer can read an object's members as nodes
 * (keeping the spelling of a number [NativeJsonTree] read) instead of having them rebuilt from tokens.
 */
private class ExactTreeParser(node: JsonNode, codec: ObjectCodec) : TreeTraversingParser(node, codec) {
    /** The node the parser is at; at a field name, that field's value. At a start-object or start-array token, null. */
    fun walkedNode(): JsonNode? = currentNode()
}

/** Binds parsed nodes with the rules of one [JsonProcessor.binderWith] call. */
class NodeBinder internal constructor(private val reader: ObjectReader, private val codec: ObjectMapper) {
    /** [node] bound into [clazz]; a number that [NativeJsonTree] read keeps its text wherever the binding keeps a node. */
    fun <T> bind(node: JsonNode, clazz: Class<T>): T = ExactTreeParser(node, codec).use { reader.readValue(it, clazz) }
}

/**
 * Binds native JSON (`.abss`, `.scene`, asset `meta.json`) to Kotlin classes: unknown properties are skipped, unknown
 * enum values take their default, and properties keep declaration order. Create one and pass it to what needs it.
 */
class JsonProcessor(internal val log: Logger) {
    private val format = JsonFormat()
    private val prettyPrinter: DefaultPrettyPrinter = format.prettyPrinter

    /** The configured mapper, for code that binds JSON itself (`readValue`, readers with injected values). */
    private val mapper: JsonMapper = format.mapperBuilder()
        .addModule(KotlinModule.Builder().build())
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        .defaultPropertyInclusion(JsonInclude.Value.ALL_NON_NULL)
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
        .registerModule(
            SimpleModule()
                .addSerializer(java.lang.Float::class.java, ComponentFloatSerializer())
                .addSerializer(java.lang.Float.TYPE, ComponentFloatSerializer())
        )

    private val componentWriter = mapper.copy()
        .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
        .addMixIn(Component::class.java, OmitComponentDefaults::class.java)
        .registerModule(
            SimpleModule()
                .addSerializer(java.lang.Float::class.java, ComponentFloatSerializer())
                .addSerializer(java.lang.Float.TYPE, ComponentFloatSerializer())
        )

    /** Default-aware component binding without exposing the processor's mapper. */
    fun <C : Component> bindComponent(node: JsonNode, type: Class<C>): C =
        componentReader.forType(type).readValue<C>(node)

    /** All modeled component values, including defaults, for comparing nested edits. */
    fun componentValues(component: Component): JsonNode = componentValues.valueToTree(component)

    /** Native component data with defaults omitted and decimal text matching scene writing. */
    fun componentTree(component: Component): JsonNode = componentWriter.valueToTree(component)

    /** [text] as a JSON tree; throws when it is not a JSON object. Numbers lose their spelling: use [NativeJsonTree] to keep it. */
    fun readObject(text: String): JsonNode =
        mapper.readTree(text)?.takeIf { it.isObject } ?: throw IllegalArgumentException("expected a JSON object")

    /**
     * A binder that reads like [bind] (warnings injected, as the components expect) and also knows the deserializers of
     * [module], for a document whose members need more than a class.
     */
    fun binderWith(module: Module): NodeBinder {
        val scene = mapper.copy().setNodeFactory(JsonNodeFactory.withExactBigDecimals(true)).registerModule(module)
        return NodeBinder(
            scene.reader(
                InjectableValues.Std().addValue(EcsReadWarnings::class.java.name, EcsReadWarnings(log))
            ), scene
        )
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
