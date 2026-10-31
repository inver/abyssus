/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementCopyCount
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementCopyIds
import net.nevinsky.abyssus.lib.core.editor.components.SetEnabledAction

/** The `ecs` row of a scene: its entities. */
class EcsNode(val scene: SceneDto) {
    val count: Int get() = scene.ecs.size
}

/** An entity row of [scene]. */
class EntityNode(val scene: SceneDto, val id: Long) {
    val entity: EntityDto? get() = scene.entity(id)
}

/** A component row of entity [entityId]: [kind] is its name as the scene file gives it (the class name). */
class ComponentNode(val scene: SceneDto, val entityId: Long, val kind: String)

private val toggles = SetEnabledAction()

/** The components of [entity] as the tree lists them, in order: the header is not one; each unmodeled one is its own row. */
fun componentNames(entity: EntityDto): List<String> = entity.components.flatMap { component ->
    when (component) {
        is HeaderComponent -> emptyList()
        is RawComponentsWrapper -> component.entries.map { it.name }
        else -> listOf(component.javaClass.simpleName)
    }
}

fun componentCountOf(entity: EntityDto): Int = componentNames(entity).size

fun hasComponent(entity: EntityDto, simpleName: String): Boolean =
    componentNames(entity).any { it.substringAfterLast('.') == simpleName }

fun isArrangementEntity(entity: EntityDto): Boolean = entity.component(PathArrangementComponent::class.java) != null

/** The eye state of an entity row: on or off for an entity that can be switched (not a `GROUP`), else null for no eye. */
fun entityEye(entity: EntityDto): Boolean? = if (toggles.isTogglable(entity)) entity.header().entityEnabled else null

/** The eye state of component [name] of [entity]: null for a component that cannot be switched. */
fun componentEye(entity: EntityDto, name: String): Boolean? {
    if (!toggles.isTogglable(name)) return null
    val header = entity.header()
    if (header.disabledComponents.any { it.simpleName == name.substringAfterLast('.') }) return false
    val raw = entity.components.filterIsInstance<RawComponentsWrapper>().flatMap { it.entries }
        .firstOrNull { it.name == name }
    return !(raw?.disabled ?: false)
}

/** The entity rows of [scene]; the linked copies of a path arrangement are squashed into its row, which counts them. */
fun ecsRows(scene: SceneDto): List<DtoRow> {
    val copies = arrangementCopyIds(scene)
    return scene.entities().filter { (id, _) -> id !in copies }
        .map { (id, entity) -> DtoRow(id.toString(), EntityNode(scene, id), enabled = entityEye(entity)) }
}

/** The component rows of an entity. */
fun entityRows(node: EntityNode): List<DtoRow> {
    val entity = node.entity ?: return emptyList()
    return componentNames(entity).map { name ->
        DtoRow(name, ComponentNode(node.scene, node.id, name), enabled = componentEye(entity, name))
    }
}

fun arrangementCopies(entity: EntityDto): Int = arrangementCopyCount(entity)
