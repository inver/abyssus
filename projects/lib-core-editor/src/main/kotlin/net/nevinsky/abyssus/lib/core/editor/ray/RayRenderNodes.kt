/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.ray

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent

/**
 * The `RenderComponent` shape the optical-override codec reads (an object holding `rayTracingMaterials`) for [entity],
 * or null when it has no render component or no overrides. The overrides node is the file's own, number text included.
 */
internal fun rayRenderNode(entity: Entity?): JsonNode? {
    val materials = entity?.getComponent(RenderComponent::class.java)?.rayTracingMaterials ?: return null
    return JsonNodeFactory.instance.objectNode().set<JsonNode>("rayTracingMaterials", materials)
}

/** The overrides of every entity that has some, by entity id. */
internal fun rayOverridesOf(ecs: Map<Long, Entity>): Map<String, JsonNode> =
    ecs.mapNotNull { (id, entity) -> rayRenderNode(entity)?.get("rayTracingMaterials")?.let { id.toString() to it } }.toMap()
