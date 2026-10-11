/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent

/*
 * The only code that knows how a scene file addresses its entities. Readers use [SceneDocument]; writers mutate the
 * tree through [SceneEntityTree]. Both share [SceneEcsPaths], so the layout has one definition (design D3/D4 of
 * `restructure-editor-modules`: the editor keeps the JSON model, games keep Ashley, both share the component codecs).
 */

private const val ECS = "ecs"
private const val ENTITIES = "entities"
private const val ARCHETYPES = "archetypes"
private const val COMPONENTS = "components"

/**
 * Where a scene file keeps its entities: `ecs.<id>.components.<ComponentName>`, or in an older scene
 * `ecs.entities.<id>.components.<ComponentName>` (an `entities` member that is an object makes the scene "wrapped").
 */
private class SceneEcsPaths {
    /** The entity map of the scene [root], or null when it has no `ecs` object. */
    fun entities(root: JsonNode): JsonNode? = entitiesIn(root.get(ECS))

    /** The entity map of a scene's `ecs` object [ecs]: its `entities` member when that is an object, else [ecs] itself. */
    fun entitiesIn(ecs: JsonNode?): JsonNode? {
        if (ecs == null || !ecs.isObject) return null
        return ecs.get(ENTITIES)?.takeIf { it.isObject } ?: ecs
    }

    /** The JSON keys leading to the entity map of [root]: `ecs`, then `entities` for a wrapped scene. */
    fun entityKeys(root: JsonNode): List<String> =
        if (root.get(ECS)?.get(ENTITIES)?.isObject == true) listOf(ECS, ENTITIES) else listOf(ECS)

    /** The `components` object of entity [entityId], or null when the entity or its components are missing. */
    fun components(root: JsonNode, entityId: String): ObjectNode? = componentsOf(entities(root)?.get(entityId))

    /** The `components` object of [entity] (an `ecs.entities` value), or null. */
    fun componentsOf(entity: JsonNode?): ObjectNode? = entity?.get(COMPONENTS) as? ObjectNode

    /** The `NameComponent.name` in [components] when it is non-blank text, else [id]. */
    fun entityName(components: JsonNode?, id: String): String =
        components?.get("NameComponent")?.get("name")?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() } ?: id
}

/** Component binding is supplied by the caller's existing runtime-compatible codec reader. */
interface SceneComponentDecoder {
    fun <C : Component> read(type: Class<C>, node: JsonNode): C
}

/** A read facade over the original tree; addresses also serve the existing tree mutation functions. */
class SceneDocument(private val root: JsonNode, private val decoder: SceneComponentDecoder? = null) {
    private val paths = SceneEcsPaths()

    init { AbyssusDocumentFormat().requireSupported(root, DocumentKind.SCENE) }

    fun entities(): List<EntityView> = entitiesNode()?.properties().orEmpty().mapNotNull { (id, node) ->
        node.takeIf { it.isObject }?.let { EntityView(id, it, decoder) }
    }
    fun entity(id: String): EntityView? = entitiesNode()?.get(id)?.takeIf { it.isObject }?.let { EntityView(id, it, decoder) }
    fun entitiesNode(): JsonNode? = paths.entities(root)
    fun entityKeys(): List<String> = paths.entityKeys(root)
    fun components(id: String): ObjectNode? = paths.components(root, id)
    fun locate(id: String): List<String>? = components(id)?.let { entityKeys() + id + COMPONENTS }
    fun renderAsset(id: String): RenderAsset? = entity(id)?.renderAsset()
    fun skybox(): String? = root.get("skyboxName")?.takeIf { root.get("skyboxEnabled")?.booleanValue() == true && it.isTextual }
        ?.textValue()?.takeIf(String::isNotBlank)

    fun lookAtTarget(id: String): Vec3? {
        val position = entity(id)?.component(PositionComponent::class.java) ?: return null
        val targetId = position.lookAtId.toString()
        if (targetId == id) return null
        val target = entity(targetId)?.component(PositionComponent::class.java)?.localPosition ?: return null
        if (!target.x.isFinite() || !target.y.isFinite() || !target.z.isFinite() || target.dst2(position.localPosition) < 1e-12f) return null
        return Vec3(target.x, target.y, target.z)
    }
}

class EntityView(val id: String, private val node: JsonNode, private val decoder: SceneComponentDecoder?) {
    val components: ObjectNode? get() = SceneEcsPaths().componentsOf(node)
    val name: String get() = SceneEcsPaths().entityName(components, id)
    fun componentNode(name: String): JsonNode? = components?.get(name)
    fun <C : Component> component(type: Class<C>): C? = componentNode(type.simpleName)?.let { value ->
        val reader = checkNotNull(decoder) { "Typed component access requires a scene component decoder" }
        runCatchingKeepingCancellation { reader.read(type, value) }.getOrNull()
    }
    fun renderAsset(): RenderAsset? = renderAssetOf(componentNode("RenderComponent"))
}

/**
 * The asset a `RenderComponent` entry names: its `type` and `assetName`. A scene saved before the render component
 * was flat nested them as `renderable.asset`, which is read too.
 */
fun renderAssetOf(render: JsonNode?): RenderAsset? {
    if (render == null) return null
    val asset = if (render.has("assetName")) render else render.get("renderable")?.get("asset") ?: return null
    val type = asset.get("type")?.takeIf { it.isTextual }?.textValue() ?: return null
    val name = asset.get("assetName")?.takeIf { it.isTextual }?.textValue()?.takeIf { it.isNotEmpty() } ?: return null
    return RenderAsset(type, name)
}

/**
 * The write-side address view of a scene tree that its caller has already admitted (writers run inside
 * `editSceneJson`, which validates first). Mutates [root] in place; it never writes a file.
 */
class SceneEntityTree(private val root: JsonNode) {
    private val paths = SceneEcsPaths()
    private val nodes = JsonNodeFactory.instance

    /** The entity map, or null when the scene has no `ecs` object. */
    fun entities(): JsonNode? = paths.entities(root)

    /** The JSON keys leading to the entity map: `ecs`, then `entities` for a wrapped scene. */
    fun entityKeys(): List<String> = paths.entityKeys(root)

    /** The `components` object of entity [entityId], or null when the entity or its components are missing. */
    fun components(entityId: String): ObjectNode? = paths.components(root, entityId)

    /** The `components` object of every entity, keyed by entity id, in file order; entities without one are skipped. */
    fun componentsById(): List<Pair<String, ObjectNode>> = entities()?.properties().orEmpty().mapNotNull { (id, entity) ->
        paths.componentsOf(entity)?.let { id to it }
    }

    /** Whether an entity can be added: a native scene whose `ecs`, when present, is an object with object tables. */
    fun canAdd(): Boolean {
        if (root !is ObjectNode || AbyssusDocumentFormat().validate(root, DocumentKind.SCENE) != null) return false
        val ecs = root.get(ECS) ?: return true
        if (ecs !is ObjectNode) return false
        return listOf(ENTITIES, ARCHETYPES).all { !ecs.has(it) || ecs.get(it) is ObjectNode }
    }

    /**
     * Adds an entity holding the components [build] makes for its id (one above the highest numeric entity id), in
     * either layout: a native entity map (`ecs` is the map), or an older `ecs.entities` block whose entities name an
     * `archetype` from `ecs.archetypes`, where it gets the archetype listing exactly those components, reusing a
     * matching one. Null, leaving the tree as it was, when the scene cannot take an entity.
     */
    fun insert(build: (id: String) -> ObjectNode): String? {
        if (!canAdd()) return null
        val ecs = root.get(ECS) as? ObjectNode ?: nodes.objectNode()
        val wrapped = isWrapped(ecs)
        val entities = if (wrapped) ecs.get(ENTITIES) as? ObjectNode ?: nodes.objectNode() else ecs
        val id = nextId(entities) ?: return null
        val entity = nodes.objectNode()
        if (wrapped) {
            if (!ecs.has(ENTITIES)) ecs.set<JsonNode>(ENTITIES, entities)
            entity.put("archetype", 0) // placeholder, set below once the components are known
        }
        entities.set<JsonNode>(id, entity.set<JsonNode>(COMPONENTS, build(id)))
        if (!root.has(ECS)) (root as ObjectNode).set<JsonNode>(ECS, ecs)
        if (wrapped && !matchArchetype(id)) {
            entities.remove(id)
            return null
        }
        return id
    }

    /**
     * Points entity [id] of a wrapped layout at the archetype listing exactly its components, adding one when none
     * matches. Does nothing (and is true) in a native layout. False when the entity or the table cannot be read.
     */
    fun matchArchetype(id: String): Boolean {
        val ecs = root.get(ECS) as? ObjectNode ?: return false
        if (!isWrapped(ecs)) return true
        val entity = (ecs.get(ENTITIES) as? ObjectNode)?.get(id) as? ObjectNode ?: return false
        val names = paths.componentsOf(entity)?.fieldNames()?.asSequence()?.toList() ?: return false
        val archetypes = ecs.get(ARCHETYPES) as? ObjectNode ?: nodes.objectNode().also { ecs.set<JsonNode>(ARCHETYPES, it) }
        val matching = archetypes.properties().firstOrNull { (key, value) ->
            key.toIntOrNull()?.toString() == key && value.isArray && value.size() == names.size &&
                value.all { it.isTextual } && value.map { it.asText() }.toSet() == names.toSet()
        }?.key
        val archetype = matching ?: nextId(archetypes)?.also { key ->
            archetypes.set<JsonNode>(key, nodes.arrayNode().also { a -> names.forEach(a::add) })
        } ?: return false
        entity.put("archetype", archetype.toInt())
        return true
    }

    private fun isWrapped(ecs: ObjectNode) = ecs.get(ENTITIES) is ObjectNode || ecs.has(ARCHETYPES)

    private fun nextId(values: ObjectNode): String? {
        val highest = values.fieldNames().asSequence().mapNotNull(String::toIntOrNull).maxOrNull() ?: -1
        return if (highest == Int.MAX_VALUE) null else (highest + 1).coerceAtLeast(0).toString()
    }
}

/** The DTO and frame feeds carry an ECS fragment from an admitted scene; reserved fields are rechecked here. */
fun sceneDocumentFromEcs(ecs: JsonNode?, decoder: SceneComponentDecoder? = null): SceneDocument {
    val root = JsonNodeFactory.instance.objectNode().put("format", "abyssus").put("formatVersion", 1)
    if (ecs != null) root.set<JsonNode>(ECS, ecs)
    return SceneDocument(root, decoder)
}

/** Ordered tree rows retain skipped JSON keys so selection and writes resolve the same location. */
data class SceneTreeRow(val name: String, val node: JsonNode, val via: List<String> = emptyList())

/** Where an entity sits under its scene's tree node: `ecs` / `<id>` (the `entities` level is not shown). */
fun sceneEntityTreePath(id: String): List<String> = listOf(ECS, id)

/** The scene's `ecs` object: a top-level entry named `ecs` holding a JSON object. */
fun isSceneEcsEntry(name: String, parents: List<String>, node: JsonNode?): Boolean =
    name == ECS && parents.isEmpty() && node?.isObject == true

/** An entity row, listed directly under `ecs`; its JSON key path is `ecs/entities` in a wrapped scene. */
fun isSceneEntityEntry(name: String, parents: List<String>, node: JsonNode?): Boolean =
    (parents == listOf(ECS, ENTITIES) || (parents == listOf(ECS) && name.toIntOrNull() != null)) && node?.isObject == true

/** A component row of an entity: its JSON key path ends in `components`. */
fun isSceneComponentEntry(parents: List<String>): Boolean =
    parents.lastOrNull() == COMPONENTS && parents.firstOrNull() == ECS &&
        (parents.size == 4 && parents[1] == ENTITIES || parents.size == 3)

/** An entity read view without typed component access, for tree rows. */
fun sceneEntityView(id: String, node: JsonNode): EntityView = EntityView(id, node, null)

/** The children of an `ecs` object: its entities directly (a wrapped scene's `entities` level is skipped), then its other keys. */
fun sceneEcsRows(ecs: JsonNode): List<SceneTreeRow> =
    if (ecs.get(ENTITIES)?.isObject != true) ecs.properties().map { (k, v) -> SceneTreeRow(k, v) } else
        ecs.get(ENTITIES).properties().map { (id, entity) -> SceneTreeRow(id, entity, listOf(ENTITIES)) } +
            ecs.properties().filter { it.key != ENTITIES || !it.value.isObject }.map { (k, v) -> SceneTreeRow(k, v) }

/** The children of an entity: its components (the `components` level is skipped); its other fields are not rows. */
fun sceneComponentRows(entity: JsonNode): List<SceneTreeRow> =
    entity.get(COMPONENTS)?.takeIf { it.isObject }?.properties()?.map { (name, node) ->
        SceneTreeRow(name, node, listOf(COMPONENTS))
    }.orEmpty()
