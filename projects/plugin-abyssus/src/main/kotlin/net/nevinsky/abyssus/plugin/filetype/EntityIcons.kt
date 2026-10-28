/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.filetype

import com.intellij.openapi.util.IconLoader
import net.nevinsky.abyssus.lib.core.dto.EntityType
import javax.swing.Icon

/** Entity silhouettes selected by the entity type; untyped entities retain the ECS icon. */
object EntityIcons {
    private val redesigned = setOf(EntityType.GROUP, EntityType.LIGHT_DIRECTIONAL, EntityType.OBJECT, EntityType.TERRAIN)

    private val icons = EntityType.entries.associateWith { type ->
        val name = when (type) {
            EntityType.GROUP -> "group"
            EntityType.LIGHT_DIRECTIONAL -> "light_directional"
            EntityType.LIGHT_POINT -> "light_point"
            EntityType.LIGHT_SPOT -> "light_spot"
            EntityType.OBJECT -> "object"
            EntityType.TERRAIN -> "terrain"
            EntityType.CAMERA -> "camera"
            EntityType.HANDLE -> "handle"
        }
        if (type in redesigned) TreeIcons.load("entity_$name")
        else IconLoader.getIcon("/icons/entity_${name}_icon.svg", EntityIcons::class.java)
    }

    fun forType(type: EntityType?): Icon = icons[type] ?: PropertyIcons.ECS
}
