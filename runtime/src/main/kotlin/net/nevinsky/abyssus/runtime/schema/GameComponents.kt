/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.schema

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.runtime.schema.BUILT_IN_COMPONENTS

/**
 * The game components of [registry], read and checked once (see [ComponentSchemaReader]): construction fails with a
 * [ComponentRegistrationException] when one cannot be registered.
 */
class GameComponents(registry: ComponentRegistry = ComponentRegistry { emptyList() }, reader: ComponentSchemaReader = ComponentSchemaReader()) {
    private val classes = registry.components()

    val schemas: List<ComponentSchema> = reader.readAll(classes, BUILT_IN_COMPONENTS)

    /** The registered component classes by the short name they are stored under. */
    @Suppress("UNCHECKED_CAST")
    val types: Map<String, Class<out Component>> = schemas.zip(classes) { schema, type -> schema.name to (type as Class<out Component>) }.toMap()

}
