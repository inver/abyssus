/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.introspect.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.nevinsky.abyssus.runtime.ecs.component.*
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.schema.Field
import net.nevinsky.abyssus.runtime.schema.GameComponents
import net.nevinsky.abyssus.runtime.schema.SceneComponent
import java.math.BigDecimal
import kotlin.math.abs

/** The short names of the components the runtime models itself; a game component cannot take one. */
val BUILT_IN_COMPONENTS: Set<String> = linkedSetOf(
    "NameComponent", "TypeComponent", "ParentComponent", "PositionComponent", "CameraComponent", "LightComponent",
    "Point2PointPositionComponent", "RenderComponent",
)

private val BUILT_IN_TYPES: List<Class<out Component>> = listOf(
    NameComponent::class.java, TypeComponent::class.java, ParentComponent::class.java, PositionComponent::class.java,
    CameraComponent::class.java, LightComponent::class.java, Point2PointPositionComponent::class.java,
    RenderComponent::class.java,
)

/**
 * The component classes a scene file may name: the built-in ones and the classes registered in [game]. A key is a fully
 * qualified class name or the short name the scene files have always used (the short class name of a built-in
 * component, the registered short name of a game component). Nothing else resolves, so a scene never loads an
 * arbitrary class.
 */
internal class ComponentTypes(game: GameComponents) {
    private val shortNames: Map<String, Class<out Component>> =
        BUILT_IN_TYPES.associateBy { it.simpleName } + game.types
    private val classNames: Map<String, Class<out Component>> = shortNames.values.associateBy { it.name }
    private val keys: Map<Class<out Component>, String> = shortNames.entries.associate { (name, type) -> type to name }

    /** Every component class a scene may hold, built-in ones first, then the game's in registration order. */
    val all: List<Class<out Component>> = shortNames.values.toList()

    /** The class [key] names; null when it names none that may load. */
    fun resolve(key: String): Class<out Component>? = classNames[key] ?: shortNames[key]

    /** The short name [type] is written under. */
    fun shortName(type: Class<out Component>): String = keys.getValue(type)
}

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

/** A component is written with only the properties that differ from a new instance's (its defaults). */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
private interface OmitDefaults

/**
 * A registered game component (a class with `@SceneComponent`) binds and writes only its `@Field`s, by field name: its
 * other members (derived state, helpers) are not part of the scene file.
 */
private class FieldsOnly : NopAnnotationIntrospector() {
    override fun findAutoDetectVisibility(ac: AnnotatedClass, checker: VisibilityChecker<*>): VisibilityChecker<*> =
        if (ac.hasAnnotation(SceneComponent::class.java)) checker.with(JsonAutoDetect.Visibility.NONE) else checker

    private fun named(member: Annotated): PropertyName? =
        if (member is AnnotatedField && member.hasAnnotation(Field::class.java)) PropertyName.USE_DEFAULT else null

    override fun findNameForSerialization(member: Annotated): PropertyName? = named(member)

    override fun findNameForDeserialization(member: Annotated): PropertyName? = named(member)
}

/** A copy of [this] mapper set up for scene components: libGDX's math types by their fields, game components by their `@Field`s. */
internal fun ObjectMapper.forEcs(): ObjectMapper {
    val copy = copy()
        .addMixIn(Vector3::class.java, PublicFieldsOnly::class.java)
        .addMixIn(Quaternion::class.java, PublicFieldsOnly::class.java)
        .addMixIn(Color::class.java, PublicFieldsOnly::class.java)
        .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
    copy.setAnnotationIntrospectors(
        AnnotationIntrospector.pair(FieldsOnly(), copy.serializationConfig.annotationIntrospector),
        AnnotationIntrospector.pair(FieldsOnly(), copy.deserializationConfig.annotationIntrospector),
    )
    return copy
}

/** [forEcs] for writing: components are written without their defaults and decimals as the scene files spell them. */
internal fun ObjectMapper.forEcsWriting(): ObjectMapper = forEcs()
    .addMixIn(Component::class.java, OmitDefaults::class.java)
    .registerModule(
        SimpleModule()
            .addSerializer(java.lang.Float::class.java, FloatSerializer())
            .addSerializer(java.lang.Float.TYPE, FloatSerializer()),
    )

/** A float, boxed or primitive, as the scene file writes it (see [number]). */
private class FloatSerializer : StdSerializer<Any>(Any::class.java) {
    override fun serialize(value: Any, gen: JsonGenerator, provider: SerializerProvider) =
        gen.writeTree(number((value as Number).toFloat()))
}

private val nodes = JsonNodeFactory.instance

/** A float as the scene file writes it: whole numbers without a fraction, others with the shortest float text. */
fun number(value: Float): JsonNode {
    val v = if (value.isFinite()) value else 0f
    return if (v == Math.rint(v.toDouble()).toFloat() && abs(v) < 1e9f) IntNode.valueOf(v.toInt()) else DecimalNode(
        BigDecimal(v.toString())
    )
}

/** An object of the [fields] that differ from their default; null when none do. */
private fun diff(vararg fields: Triple<String, Float, Float>): ObjectNode? {
    val out = nodes.objectNode()
    for ((name, value, default) in fields) if (value != default) out.set<JsonNode>(name, number(value))
    return out.takeIf { it.size() > 0 }
}

internal fun vectorDiff(v: Vector3, default: Float = 0f) =
    diff(Triple("x", v.x, default), Triple("y", v.y, default), Triple("z", v.z, default))

internal fun quaternionDiff(q: Quaternion) =
    diff(Triple("x", q.x, 0f), Triple("y", q.y, 0f), Triple("z", q.z, 0f), Triple("w", q.w, 1f))

internal fun colorNode(c: net.nevinsky.abyssus.core.scene.Color): ObjectNode = nodes.objectNode()
    .set<ObjectNode>("r", number(c.r)).set<ObjectNode>("g", number(c.g))
    .set<ObjectNode>("b", number(c.b)).set<ObjectNode>("a", number(c.a))

internal fun ObjectNode.putIf(name: String, node: JsonNode?) = apply { if (node != null) set<JsonNode>(name, node) }

internal fun ObjectNode.putId(name: String, id: Int) =
    apply { if (id != EcsUtils.NO_ENTITY) set<JsonNode>(name, IntNode.valueOf(id)) }
