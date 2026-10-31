/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent

/** One change of a scene: it edits [SceneDto] in place and returns whether it changed anything. */
fun interface SceneEdit {
    fun apply(scene: SceneDto): Boolean
}

/** A new entity with the id [id] and the type [type]: its header, then [parts] in order. */
fun newEntity(id: Long, type: EntityType, vararg parts: Component): EntityDto =
    EntityDto(mutableListOf<Component>(HeaderComponent(id = id, type = type)).apply { addAll(parts) })
