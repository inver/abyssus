/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.CameraComponent
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementCopyCount
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementCopyIds
import net.nevinsky.abyssus.lib.core.editor.components.SetEnabledAction
import net.nevinsky.abyssus.lib.core.editor.hierarchy.SceneHierarchy
import net.nevinsky.abyssus.lib.core.format.EntityType

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

/**
 * The components of [entity] as the tree lists them, in order: the header is not one; each unmodeled one is its own
 * row.
 */
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

/** True for an entity that is a camera, by type or by carrying a [CameraComponent] (a plain object otherwise). */
fun isCameraEntity(entity: EntityDto): Boolean {
    val type = entity.header().type
    return type == EntityType.CAMERA ||
        ((type == null || type == EntityType.OBJECT) && entity.components.any { it is CameraComponent })
}

/** True for a light, by type or by carrying a [LightComponent]. */
fun isLightEntity(entity: EntityDto): Boolean {
    val type = entity.header().type
    return type == EntityType.LIGHT_DIRECTIONAL || type == EntityType.LIGHT_POINT || type == EntityType.LIGHT_SPOT ||
        ((type == null || type == EntityType.OBJECT) && entity.components.any { it is LightComponent })
}

/** True for a plain object that renders a model, which is not a camera or a light. */
fun isModelEntity(entity: EntityDto): Boolean {
    val type = entity.header().type
    return (type == null || type == EntityType.OBJECT) && !isCameraEntity(entity) && !isLightEntity(entity) &&
        entity.components.any { it is RenderComponent }
}

/** A camera, light or model is a leaf in the tree: its components are edited in Properties. */
fun isLeafEntity(entity: EntityDto): Boolean = isCameraEntity(entity) || isLightEntity(entity) || isModelEntity(entity)

fun isArrangementEntity(entity: EntityDto): Boolean = entity.component(PathArrangementComponent::class.java) != null

/**
 * The eye state of an entity row: on or off for an entity that can be switched (not a `GROUP`), else null for no eye.
 */
fun entityEye(entity: EntityDto): Boolean? = if (toggles.isTogglable(entity)) {
    entity.header().entityEnabled
} else {
    null
}

/** The eye state of component [name] of [entity]: null for a component that cannot be switched. */
fun componentEye(entity: EntityDto, name: String): Boolean? {
    if (!toggles.isTogglable(name)) {
        return null
    }
    val header = entity.header()
    if (header.disabledComponents.any { it.simpleName == name.substringAfterLast('.') }) {
        return false
    }
    val raw = entity.components.filterIsInstance<RawComponentsWrapper>().flatMap { it.entries }
        .firstOrNull { it.name == name }
    return !(raw?.disabled ?: false)
}

/**
 * The parent of each child entity of [scene] whose link is followed (OpenSpec `abyssus-project-view`, "Child entities
 * are listed under their parent"): a valid parent, not itself, not a cycle, and not a squashed arrangement copy.
 */
fun treeParents(scene: SceneDto): Map<Long, Long> {
    val copies = arrangementCopyIds(scene)
    return SceneHierarchy().resolve(scene).parents.filterKeys { it !in copies }
}

/** The ids of [id]'s child entities in the tree, in id order. */
fun treeChildren(scene: SceneDto, id: Long): List<Long> =
    treeParents(scene).filterValues { it == id }.keys.sorted()

/**
 * The root entity rows of [scene]: a child is listed under its parent instead, and the linked copies of a path
 * arrangement are squashed into its row, which counts them. A broken parent link leaves the entity here.
 */
fun ecsRows(scene: SceneDto): List<DtoRow> {
    val copies = arrangementCopyIds(scene)
    val parents = treeParents(scene)
    return scene.entities().filter { (id, _) -> id !in copies && id !in parents }
        .map { (id, entity) -> DtoRow(id.toString(), EntityNode(scene, id), enabled = entityEye(entity)) }
}

/** The child entity rows of an entity, in id order. */
fun childEntityRows(node: EntityNode): List<DtoRow> =
    treeChildren(node.scene, node.id).mapNotNull { id ->
        node.scene.entity(id)?.let { DtoRow(id.toString(), EntityNode(node.scene, id), enabled = entityEye(it)) }
    }

/** The rows of an entity: its child entities first, then its components. */
fun entityRows(node: EntityNode): List<DtoRow> {
    val entity = node.entity ?: return emptyList()
    return childEntityRows(node) + componentNames(entity).map { name ->
        DtoRow(name, ComponentNode(node.scene, node.id, name), enabled = componentEye(entity, name))
    }
}

fun arrangementCopies(entity: EntityDto): Int = arrangementCopyCount(entity)
