/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import net.nevinsky.abyssus.lib.core.ecs.component.IdComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.Point2PointPositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.io.EcsReadWarnings
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.EcsLoadingWarns
import net.nevinsky.abyssus.lib.core.scene.SceneEngine
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.NO_ENTITY
import net.nevinsky.abyssus.lib.core.util.obj

/**
 * Loads the `ecs` block of a scene file into a [SceneEngine] with Jackson: each entry of an entity's `components` is
 * keyed by the class name of its component, and its value is bound with [ObjectMapper.readerFor] `.readValue` into
 * that class, with no per-component codec. Components configure their own binding with Jackson annotations (a vector
 * merges into the component's own, a render component resolves its asset through the [net.nevinsky.abyssus.lib.runtime.ecs.render.AssetResolver] injected into the
 * reader).
 *
 * Game components are bound the same way: a registered class needs a no-argument constructor, only its `@Field`s are
 * bound (by field name), and a value Jackson cannot bind (the wrong type, an unknown enum name) keeps the whole
 * component raw.
 *
 * A key names a class either fully qualified (`net.nevinsky.abyssus.lib.runtime.ecs.component.NameComponent`) or by the short
 * class name the scene files have always used (`NameComponent`, or the short name of a registered game component). Only
 * the built-in components and the classes registered in [game] can be named: a scene is project data, so a class name
 * in it never loads an arbitrary class. Anything else, and a component whose value Jackson cannot bind, is carried raw
 * (see [EcsLoadingWarns.carried]) with one warning, so a scene can be written back unchanged. References to ids that are
 * not in the file become [NO_ENTITY].
 *
 * An `ecs` block is the entity map itself (`{"0": {...}, "1": {...}}`); an older block wraps it in an `entities` member
 * beside others (`metadata`), which the document keeps and the writer puts back, so a scene keeps the shape it had.
 *
 * The Mundus-era `archetype` of an entity and the `archetypes` table of the block are not read and not carried: Ashley
 * has no archetypes, and nothing in the runtime uses them, so a scene written back no longer has them.
 */
class EcsLoader(
    private val json: JsonProcessor,
    private val componentRegistry: ComponentRegistry,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
) {

    fun loadToEngine(ecs: JsonNode, engine: SceneEngine): EcsLoadingWarns {
        format.requireEcs(ecs)

        val readerAndWarnings = json.ecsReader()
        val carried = HashMap<Long, Map<String, JsonNode>>()

        ecs.obj("entities")?.properties()?.forEach { (key, value) ->
            val id = key.toIntOrNull()
            if (id == null) {
                readerAndWarnings.second.warn("entity id '$key' is not a number; the entity is skipped")
                return@forEach
            }
            val carriedRaw = HashMap<String, JsonNode>()
            val entity = readEntity(id, value, readerAndWarnings, carriedRaw)
            if (carriedRaw.isNotEmpty()) {
                carried[id.toLong()] = carriedRaw
            }
            engine.addEntity(entity)
            engine.ids.register(id, entity)
        }
        resolveReferences(engine, readerAndWarnings)
        return EcsLoadingWarns(readerAndWarnings.second.messages, carried)
    }

    /** The entity of [id]; what it cannot bind goes to [carried] under the key the file gave it. */
    private fun readEntity(
        id: Int,
        node: JsonNode,
        readerAndWarnings: Pair<ObjectReader, EcsReadWarnings>,
        carried: MutableMap<String, JsonNode>,
    ): Entity {
        val entity = Entity()
        entity.add(IdComponent(id.toLong()))
        node.obj("components")?.properties()?.forEach { (name, value) ->
            val type = componentRegistry.get(name)
            if (type == null) {
                carried[name] = value
                readerAndWarnings.second.warn("component $name is not modeled and is kept unchanged")
                return@forEach
            }
            val component = try {
                readerAndWarnings.first.readValue(value, type)
            } catch (e: Exception) {
                carried[name] = value
                readerAndWarnings.second.warn(
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

    private fun resolveReferences(engine: SceneEngine, warnings: Pair<ObjectReader, EcsReadWarnings>) {
        for (entity in engine.entities) {
            val from = entity.getComponent(IdComponent::class.java).id
            entity.getComponent(PositionComponent::class.java)
                ?.let { it.lookAtId = check(engine, warnings, from, "look-at", it.lookAtId) }
            entity.getComponent(ParentComponent::class.java)
                ?.let { it.parentEntityId = check(engine, warnings, from, "parent", it.parentEntityId) }
            entity.getComponent(Point2PointPositionComponent::class.java)?.let {
                it.entity1Id = check(engine, warnings, from, "point-to-point entity1", it.entity1Id)
                it.entity2Id = check(engine, warnings, from, "point-to-point entity2", it.entity2Id)
            }
        }
    }

    private fun check(
        engine: SceneEngine,
        warnings: Pair<ObjectReader, EcsReadWarnings>,
        from: Long,
        kind: String,
        target: Int
    ): Int {
        if (target == NO_ENTITY || target in engine.ids) {
            return target
        }
        warnings.second.warn("entity $from: $kind refers to entity $target, which is not in the scene")
        return NO_ENTITY
    }
}
