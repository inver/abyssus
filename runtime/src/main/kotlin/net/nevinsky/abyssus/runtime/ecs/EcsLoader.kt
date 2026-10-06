/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs

import net.nevinsky.abyssus.core.format.AbyssusDocumentFormat
import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.databind.InjectableValues
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import net.nevinsky.abyssus.runtime.ecs.EcsUtils.Companion.NO_ENTITY
import net.nevinsky.abyssus.runtime.ecs.component.*
import net.nevinsky.abyssus.runtime.ecs.render.AssetResolver
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsDocument
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsWarnings
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEngine
import net.nevinsky.abyssus.runtime.obj
import net.nevinsky.abyssus.runtime.schema.GameComponents
import org.slf4j.Logger

/**
 * Loads the `ecs` block of a scene file into a [SceneEngine] with Jackson: each entry of an entity's `components` is
 * keyed by the class name of its component, and its value is bound with [ObjectMapper.readerFor] `.readValue` into
 * that class, with no per-component codec. Components configure their own binding with Jackson annotations (a vector
 * merges into the component's own, a render component resolves its asset through the [AssetResolver] injected into the
 * reader).
 *
 * Game components are bound the same way: a registered class needs a no-argument constructor, only its `@Field`s are
 * bound (by field name), and a value Jackson cannot bind (the wrong type, an unknown enum name) keeps the whole
 * component raw.
 *
 * A key names a class either fully qualified (`net.nevinsky.abyssus.runtime.ecs.component.NameComponent`) or by the short
 * class name the scene files have always used (`NameComponent`, or the short name of a registered game component). Only
 * the built-in components and the classes registered in [game] can be named: a scene is project data, so a class name
 * in it never loads an arbitrary class. Anything else, and a component whose value Jackson cannot bind, is carried raw
 * (see [SceneEcsDocument.carried]) with one warning, so a scene can be written back unchanged. References to ids that are
 * not in the file become [NO_ENTITY].
 *
 * An `ecs` block is the entity map itself (`{"0": {...}, "1": {...}}`); an older block wraps it in an `entities` member
 * beside others (`metadata`), which the document keeps and the writer puts back, so a scene keeps the shape it had.
 *
 * The Mundus-era `archetype` of an entity and the `archetypes` table of the block are not read and not carried: Ashley
 * has no archetypes, and nothing in the runtime uses them, so a scene written back no longer has them.
 */
class EcsLoader(
    mapper: ObjectMapper,
    private val resolver: AssetResolver = AssetResolver { _, _ -> null },
    private val log: Logger,
    game: GameComponents = GameComponents(),
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
) {
    /**
     * A copy of the caller's mapper set up for components (see [forEcs]) that also merges an object into the value a
     * property already holds, so a vector or color the file only partly names keeps the rest of the component's own
     * default, and keeps decimal text (`7.000`), which a component that carries the file's own node (a light, a
     * render component) writes back.
     */
    private val mapper: ObjectMapper = mapper.forEcs().setDefaultMergeable(true)

    private val types = ComponentTypes(game)

    /** The component class [key] names (a fully qualified class name or a short name); null when it names none that may load. */
    fun componentClass(key: String): Class<out Component>? = types.resolve(key)

    fun load(ecs: JsonNode, engine: SceneEngine): SceneEcsDocument {
        format.requireEcs(ecs)
        val warnings = SceneEcsWarnings(log)
        val reader = mapper.reader(
            InjectableValues.Std()
                .addValue(AssetResolver::class.java.name, resolver)
                .addValue(SceneEcsWarnings::class.java.name, warnings),
        )

        val carried = LinkedHashMap<Long, Map<String, JsonNode>>()
        // the entities are the block itself, or (older scenes) the members of its `entities` object next to other members
        val wrapped = ecs.obj("entities") != null
        (if (wrapped) ecs.obj("entities") else ecs)?.properties()?.forEach { (key, node) ->
            val id = key.toIntOrNull()
            if (id == null) {
                warnings.warn("entity id '$key' is not a number; the entity is skipped")
                return@forEach
            }
            val raw = LinkedHashMap<String, JsonNode>()
            val entity = readEntity(id, node, reader, warnings, raw)
            if (raw.isNotEmpty()) carried[id.toLong()] = raw
            engine.addEntity(entity)
            engine.ids.register(id, entity)
        }
        resolveReferences(engine, warnings)

        val extras = LinkedHashMap<String, JsonNode>()
        if (wrapped) ecs.properties()
            .forEach { (key, node) -> if (key != "entities" && key != "archetypes") extras[key] = node }
        return SceneEcsDocument(extras, warnings.messages, carried, wrapped)
    }

    /** The entity of [id]; what it cannot bind goes to [carried] under the key the file gave it. */
    private fun readEntity(
        id: Int,
        node: JsonNode,
        reader: ObjectReader,
        warnings: SceneEcsWarnings,
        carried: MutableMap<String, JsonNode>,
    ): Entity {
        val entity = Entity()
        entity.add(IdComponent(id.toLong()))
        node.obj("components")?.properties()?.forEach { (name, value) ->
            val type = componentClass(name)
            if (type == null) {
                carried[name] = value
                warnings.warn("component $name is not modeled and is kept unchanged")
                return@forEach
            }
            val component = try {
                reader.forType(type).readValue<Component>(value)
            } catch (e: Exception) {
                carried[name] = value
                warnings.warn(
                    "entity $id: component $name could not be read (${
                        e.message?.lineSequence()?.first()
                    }) and is kept unchanged"
                )
                return@forEach
            }
            entity.add(component)
        }
        return entity
    }

    private fun resolveReferences(engine: SceneEngine, warnings: SceneEcsWarnings) {
        fun check(from: Long, kind: String, target: Int): Int {
            if (target == NO_ENTITY || target in engine.ids) return target
            warnings.warn("entity $from: $kind refers to entity $target, which is not in the scene")
            return NO_ENTITY
        }
        for (entity in engine.entities) {
            val from = entity.getComponent(IdComponent::class.java).id
            entity.getComponent(PositionComponent::class.java)
                ?.let { it.lookAtId = check(from, "look-at", it.lookAtId) }
            entity.getComponent(ParentComponent::class.java)
                ?.let { it.parentEntityId = check(from, "parent", it.parentEntityId) }
            entity.getComponent(Point2PointPositionComponent::class.java)?.let {
                it.entity1Id = check(from, "point-to-point entity1", it.entity1Id)
                it.entity2Id = check(from, "point-to-point entity2", it.entity2Id)
            }
        }
    }
}
