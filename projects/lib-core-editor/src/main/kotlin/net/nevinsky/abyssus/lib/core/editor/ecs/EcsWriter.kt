package net.nevinsky.abyssus.lib.gdx.editor.ecs

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.nevinsky.abyssus.lib.gdx.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.gdx.ecs.component.IdComponent
import net.nevinsky.abyssus.lib.gdx.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.gdx.scene.EcsLoadingWarns
import net.nevinsky.abyssus.lib.gdx.scene.SceneEngine
import java.math.BigDecimal
import kotlin.math.abs

/** A component is written with only the properties that differ from a new instance's (its defaults). */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
private interface OmitDefaults

/** A float as the scene files spell it: a whole number as an integer (`22`, not `22.0`), else its shortest decimal. */
internal fun floatNode(value: Float): JsonNode {
    val v = if (value.isFinite()) value else 0f
    return if (v == Math.rint(v.toDouble()).toFloat() && abs(v) < 1e9f) IntNode.valueOf(v.toInt())
    else DecimalNode(BigDecimal(v.toString()))
}

/** A float, boxed or primitive, as the scene file writes it (see [floatNode]). */
private class FloatSerializer : StdSerializer<Any>(Any::class.java) {
    override fun serialize(value: Any, gen: JsonGenerator, provider: SerializerProvider) =
        gen.writeTree(floatNode((value as Number).toFloat()))
}

/** A copy of [this] mapper for writing components: without their defaults, decimals as the scene files spell them. */
private fun ObjectMapper.forEcsWriting(): ObjectMapper = copy()
    .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
    .addMixIn(Component::class.java, OmitDefaults::class.java)
    .registerModule(
        SimpleModule()
            .addSerializer(java.lang.Float::class.java, FloatSerializer())
            .addSerializer(java.lang.Float.TYPE, FloatSerializer()),
    )

/**
 * Writes a [SceneEngine] as an `ecs` block in the native format, the counterpart of
 * [net.nevinsky.abyssus.lib.gdx.ecs.EcsLoader]: every component is turned into JSON by Jackson, with no per-component
 * codec.
 *
 * - Entities are written in ascending order of their [IdComponent]'s id; an entity without one follows, numbered after
 *   the largest id.
 * - A component is written without the properties that equal a new instance's (its defaults), and a decimal the way
 *   the scene files spell it (`22`, not `22.0`), under the name [registry] gives it, built-in components first.
 * - What the loader could not bind (kept in [EcsLoadingWarns.carried]) is added after the components, under the key
 *   the file gave it, unchanged.
 * - The block is `{"entities": {...}}`.
 * - Derived state (combined transform, light instance, point-to-point positions) is not written.
 */
class EcsWriter(
    mapper: ObjectMapper,
    private val registry: ComponentRegistry = ComponentRegistry(),
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
) {
    private val mapper: ObjectMapper = mapper.forEcsWriting()
    private val nodes = JsonNodeFactory.instance

    fun write(engine: SceneEngine, document: EcsLoadingWarns): ObjectNode {
        val entities = nodes.objectNode()
        for ((id, entity) in byId(engine)) entities.set<JsonNode>(id.toString(), writeEntity(id, entity, document))
        val out = nodes.objectNode()
        out.set<JsonNode>("entities", entities)
        format.requireEcs(out)
        return out
    }

    /** [component] as the scene file holds it: the value of its entry in an entity's `components`. */
    fun writeComponent(component: Component): JsonNode = mapper.valueToTree(component)

    /**
     * The entities of [engine] with the id they are written under, ascending by their [net.nevinsky.abyssus.lib.gdx.ecs.component.IdComponent]'s id, so the order
     * of the result never depends on the order entities were added in. An entity without an [net.nevinsky.abyssus.lib.gdx.ecs.component.IdComponent] follows, in
     * engine order, numbered after the largest id.
     */
    private fun byId(engine: SceneEngine): List<Pair<Long, Entity>> {
        val withId = engine.entities.mapNotNull { e -> e.getComponent(IdComponent::class.java)?.let { it.id to e } }
            .sortedBy { it.first }
        var nextId = (withId.lastOrNull()?.first ?: -1L) + 1
        val withoutId =
            engine.entities.filter { it.getComponent(IdComponent::class.java) == null }.map { nextId++ to it }
        return withId + withoutId
    }

    private fun writeEntity(id: Long, entity: Entity, document: EcsLoadingWarns): ObjectNode {
        val components = nodes.objectNode()
        for (type in registry.types) {
            entity.getComponent(type)?.let { components.set<JsonNode>(registry.nameOf(type), writeComponent(it)) }
        }
        // what the loader could not bind, after the components: unless the entity has a component of that class now
        document.carried[id]?.forEach { (key, node) ->
            val type = registry.get(key)
            if ((type == null || entity.getComponent(type) == null) && !components.has(key)) components.set<JsonNode>(
                key,
                node
            )
        }
        return nodes.objectNode().set<ObjectNode>("components", components)
    }
}