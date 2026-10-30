/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.filetype

import com.intellij.openapi.util.IconLoader
import net.nevinsky.abyssus.lib.core.ecs.component.TypeComponent.Type
import javax.swing.Icon

/** Entity silhouettes selected by TypeComponent; untyped entities retain the ECS icon. */
object EntityIcons {
    private val redesigned = setOf(Type.GROUP, Type.LIGHT_DIRECTIONAL, Type.OBJECT, Type.TERRAIN)

    private val icons = Type.entries.associateWith { type ->
        val name = when (type) {
            Type.GROUP -> "group"
            Type.LIGHT_DIRECTIONAL -> "light_directional"
            Type.LIGHT_POINT -> "light_point"
            Type.LIGHT_SPOT -> "light_spot"
            Type.OBJECT -> "object"
            Type.TERRAIN -> "terrain"
            Type.CAMERA -> "camera"
            Type.HANDLE -> "handle"
        }
        if (type in redesigned) TreeIcons.load("entity_$name")
        else IconLoader.getIcon("/icons/entity_${name}_icon.svg", EntityIcons::class.java)
    }

    fun forType(type: Type?): Icon = icons[type] ?: PropertyIcons.ECS
}
