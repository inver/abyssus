/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree

/** A copied entity as clipboard text, or the reason it cannot be copied. */
sealed interface CopyResult {
    data class Copied(val text: String) : CopyResult
    data class Refused(val reason: String) : CopyResult
}

/**
 * Copies a model entity of a scene as text and pastes it, in this or another scene, as a new entity. The text carries the
 * entity's components as they are; a paste only gives them a new id and moves the copy to the pasted point. A terrain, an
 * entity without a render component and a path arrangement and an entity with a parent are not copied, because their links to other
 * entities would not hold in a copy. Edits only the JSON tree, as [AssetEntities] does.
 */
class EntityClipboard(private val messages: EditorMessages) {
    private val mapper = ObjectMapper()

    /** The clipboard text for entity [entityId] of [root]. */
    fun copy(root: JsonNode, entityId: String): CopyResult {
        val components = SceneEntityTree(root).components(entityId)
        val refusal = CopyResult.Refused(messages.message("entityClipboardNotCopyable", entityId))
        if (components == null || !components.has(RENDER) || KEEP_OUT.any(components::has)) return refusal
        if (components.get("TypeComponent")?.get("type")?.asText() == "TERRAIN") return refusal
        val envelope = mapper.createObjectNode().put(MARKER, VERSION).set<JsonNode>("components", components.deepCopy())
        return CopyResult.Copied(mapper.writeValueAsString(envelope))
    }

    /** Whether [text] is what [copy] makes. */
    fun holdsEntity(text: String?): Boolean = parse(text) != null

    /** Adds the entity [text] holds to [root] with its pivot at [point]; rejected, leaving [root] as it was, otherwise. */
    fun paste(root: JsonNode, text: String?, point: Vector3): AddedEntity {
        val components = parse(text)
            ?: return AddedEntity(EditResult.Rejected(messages.message("entityClipboardNotEntity")))
        if (listOf(point.x, point.y, point.z).any { !it.isFinite() }) {
            return AddedEntity(EditResult.Rejected(messages.message("componentSceneUnreadable")))
        }
        val id = SceneEntityTree(root).insert { components.deepCopy().also { place(it, point) } }
            ?: return AddedEntity(EditResult.Rejected(messages.message("componentSceneUnreadable")))
        return AddedEntity(EditResult.Changed, id)
    }

    /** Sets `PositionComponent.localPosition` of [components] to [point], keeping the component's other members. */
    private fun place(components: ObjectNode, point: Vector3) {
        val position = components.get("PositionComponent") as? ObjectNode ?: mapper.createObjectNode().also { components.set<JsonNode>("PositionComponent", it) }
        position.set<JsonNode>("localPosition", mapper.createObjectNode().put("x", point.x).put("y", point.y).put("z", point.z))
    }

    private fun parse(text: String?): ObjectNode? {
        val envelope = runCatchingKeepingCancellation { mapper.readTree(text ?: return null) }.getOrNull() as? ObjectNode ?: return null
        if (envelope.get(MARKER)?.asInt() != VERSION) return null
        val components = envelope.get("components") as? ObjectNode ?: return null
        return components.takeIf { it.has(RENDER) && KEEP_OUT.none(it::has) }
    }
}

private const val MARKER = "abyssusEntity"
private const val VERSION = 1
private const val RENDER = "RenderComponent"
private val KEEP_OUT = listOf("PathArrangementComponent", "ParentComponent")
