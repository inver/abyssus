package net.nevinsky.abyssus.lib.core.editor.ecs

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.dto.ENTITY_DISABLED_FLAG
import net.nevinsky.abyssus.lib.core.format.FLAGS_KEY
import net.nevinsky.abyssus.lib.core.ecs.EcsLoadingWarns
import net.nevinsky.abyssus.lib.core.ecs.SceneEngine
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import java.math.BigDecimal
import kotlin.math.abs

/** A float as the scene files spell it: a whole number as an integer (`22`, not `22.0`), else its shortest decimal. */
internal fun floatNode(value: Float): JsonNode {
    val v = if (value.isFinite()) value else 0f
    return if (v == Math.rint(v.toDouble()).toFloat() && abs(v) < 1e9f) IntNode.valueOf(v.toInt())
    else DecimalNode(BigDecimal(v.toString()))
}

/**
 * Writes a [SceneEngine] as an `ecs` block in the native format, the counterpart of
 * [net.nevinsky.abyssus.lib.core.ecs.EcsLoader]: every component is turned into JSON by Jackson, with no per-component
 * codec.
 *
 * - Entities are written in ascending order of their [HeaderComponent]'s id; an entity without one follows, numbered after
 *   the largest id.
 * - A component is written without the properties that equal a new instance's (its defaults), and a decimal the way
 *   the scene files spell it (`22`, not `22.0`), under the name [registry] gives it, built-in components first.
 * - What the loader could not bind (kept in [EcsLoadingWarns.carried]) is added after the components, under the key
 *   the file gave it, unchanged.
 * - The block is the id-keyed entity map; an entity is `{"components": {...}}`, plus `flags` when it has a bit set.
 * - Derived state (combined transform, light instance) is not written.
 */
class EcsWriter(
    private val jsonProcessor: JsonProcessor,
    private val registry: ComponentRegistry = ComponentRegistry(),
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
) {
    private val nodes = JsonNodeFactory.instance

    fun write(engine: SceneEngine, document: EcsLoadingWarns): ObjectNode {
        val entities = nodes.objectNode()
        for ((id, entity) in byId(engine)) {
            entities.set<JsonNode>(id.toString(), writeEntity(id, entity, document))
        }
        format.requireEcs(entities)
        return entities
    }

    /** [component] as the scene file holds it: the value of its entry in an entity's `components`. */
    fun writeComponent(component: Component): JsonNode = jsonProcessor.componentTree(component)

    /**
     * The entities of [engine] with the id they are written under, ascending by their [HeaderComponent]'s id, so the order
     * of the result never depends on the order entities were added in. An entity without an [HeaderComponent] follows, in
     * engine order, numbered after the largest id.
     */
    private fun byId(engine: SceneEngine): List<Pair<Long, Entity>> {
        val withId = engine.entities.mapNotNull { e ->
            e.getComponent(HeaderComponent::class.java)?.let { it.id to e }
        }.sortedBy { it.first }
        var nextId = (withId.lastOrNull()?.first ?: -1L) + 1
        val withoutId = engine.entities.filter { it.getComponent(HeaderComponent::class.java) == null }
            .map { nextId++ to it }
        return withId + withoutId
    }

    private fun writeEntity(id: Long, entity: Entity, document: EcsLoadingWarns): ObjectNode {
        val components = nodes.objectNode()
        val header = entity.getComponent(HeaderComponent::class.java)
        val disabled = ArrayList<String>()
        for (type in registry.types) {
            entity.getComponent(type)?.let {
                components.set<JsonNode>(registry.nameOf(type), writeComponent(it))
                if (header != null && type in header.disabledComponents) disabled += registry.nameOf(type)
            }
        }
        // what the loader could not bind, after the components: unless the entity has a component of that class now
        document.carried[id]?.forEach { (key, raw) ->
            val type = registry.get(key)
            if ((type == null || entity.getComponent(type) == null) && !components.has(key)) {
                components.set<JsonNode>(key, SceneJson().parse(raw.json))
                if (raw.disabled) disabled += key
            }
        }
        // the header comes first: type, flags, disabledComponents; then the components
        val out = nodes.objectNode()
        header?.type?.let { out.put("type", it.name) }
        if (header != null) {
            val flags = header.flags or if (header.entityEnabled) 0 else ENTITY_DISABLED_FLAG
            if (flags != 0) out.put(FLAGS_KEY, flags)
        }
        if (disabled.isNotEmpty()) out.putArray("disabledComponents").also { list -> disabled.forEach(list::add) }
        out.set<JsonNode>("components", components)
        return out
    }
}
