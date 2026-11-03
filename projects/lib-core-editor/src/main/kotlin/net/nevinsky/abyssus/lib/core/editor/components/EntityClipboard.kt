/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.PlacementWrites
import net.nevinsky.abyssus.lib.core.editor.util.Util
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/** A copied entity as clipboard text, or the reason it cannot be copied. */
sealed interface CopyResult {
    data class Copied(val text: String) : CopyResult
    data class Refused(val reason: String) : CopyResult
}

/**
 * Copies a model entity of a scene as text and pastes it, in this or another scene, as a new entity. The text carries the
 * entity's components as they are, each under the name a scene file gives it with its JSON as text; a paste only gives them a new id and moves
 * the copy to the pasted point. A terrain, an entity without a render component, a path arrangement and an entity with a
 * parent are not copied, because their links to other entities would not hold in a copy. A component this editor does
 * not model travels as the text it was read from. Edits only the [SceneDto], as [AssetEntities] does.
 */
class EntityClipboard(
    private val json: JsonProcessor,
    private val messages: EditorMessages,
    private val registry: ComponentRegistry = ComponentRegistry(),
) {
    private val placements = PlacementWrites()

    /** The clipboard text for entity [entityId] of [scene]. */
    fun copy(scene: SceneDto, entityId: Long): CopyResult {
        val refusal = CopyResult.Refused(messages.message("entityClipboardNotCopyable", entityId))
        val entity = scene.entity(entityId) ?: return refusal
        val type = entity.component(HeaderComponent::class.java)?.type
        if (type == EntityType.TERRAIN || entity.component(RenderComponent::class.java) == null) {
            return refusal
        }
        if (KEEP_OUT.any { entity.component(it) != null }) {
            return refusal
        }
        val components = runCatchingKeepingCancellation {
            entity.components.filter { it !is HeaderComponent && it !is RawComponentsWrapper }
                .map { ClipboardComponent(registry.nameOf(it.javaClass), json.toString(it)) } +
                    entity.components.filterIsInstance<RawComponentsWrapper>().flatMap { it.entries }
                        .map { ClipboardComponent(it.name, it.json) }
        }.getOrElse { return refusal }
        return CopyResult.Copied(json.toString(ClipboardEntity(VERSION, type, components)))
    }

    /** Whether [text] is what [copy] makes. */
    fun holdsEntity(text: String?): Boolean = parse(text) != null

    /** Adds the entity [text] holds to [scene] with its pivot at [point]; rejected, leaving [scene] as it was, otherwise. */
    fun paste(scene: SceneDto, text: String?, point: Vector3): AddedEntity {
        val copied =
            parse(text) ?: return AddedEntity(EditResult.Rejected(messages.message("entityClipboardNotEntity")))
        if (!Util.isFinite(point)) {
            return AddedEntity(EditResult.Rejected(messages.message("componentSceneUnreadable")))
        }
        val typed = ArrayList<Component>()
        val raw = RawComponentsWrapper()
        for ((name, text) in copied.components) {
            val type = registry.get(name)
            val component = type?.let { runCatchingKeepingCancellation { json.parse(text, it) }.getOrNull() }
            if (component != null) {
                typed += component
            } else {
                raw.entries += RawComponent(name, text, "component $name is kept unchanged")
            }
        }
        val position = typed.filterIsInstance<PositionComponent>().firstOrNull() ?: PositionComponent().also(typed::add)
        placements.setPosition(position, point)
        if (raw.entries.isNotEmpty()) {
            typed += raw
        }
        val id = scene.add(copied.type ?: EntityType.OBJECT) { typed }
        return AddedEntity(EditResult.Changed, id)
    }

    /** The envelope [text] holds when it is what [copy] makes, else null. */
    private fun parse(text: String?): ClipboardEntity? {
        val entity = runCatchingKeepingCancellation { json.parse(text ?: return null, ClipboardEntity::class.java) }
            .getOrNull() ?: return null
        if (entity.abyssusEntity != VERSION) {
            return null
        }
        val names = entity.components.map { it.name.substringAfterLast('.') }
        return entity.takeIf { RENDER in names && KEEP_OUT.none { out -> out.simpleName in names } }
    }

    /** The clipboard text: a marker and version, the entity's type and its components by file name. */
    private data class ClipboardEntity(
        val abyssusEntity: Int = 0,
        val type: EntityType? = null,
        val components: List<ClipboardComponent> = emptyList(),
    )

    /** One component: its file name and its own JSON, kept as text so the clipboard never holds a tree. */
    private data class ClipboardComponent(val name: String, val json: String)
}

private const val VERSION = 1
private val RENDER = RenderComponent::class.java.simpleName
private val KEEP_OUT = listOf(PathArrangementComponent::class.java, ParentComponent::class.java)
