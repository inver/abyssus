/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.schema

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.runtime.ecs.scene.BUILT_IN_COMPONENTS
import net.nevinsky.abyssus.runtime.ecs.scene.ComponentCodec

/**
 * Reads and writes a game component class through [SchemaJson]: read decodes to values and sets the `@Field`s of a new
 * instance, write reads them back and encodes. A thin layer, so the game and the editor write the same text.
 */
class ReflectiveCodec<C : Component>(
    val schema: ComponentSchema,
    override val type: Class<C>,
    private val json: SchemaJson = SchemaJson(),
) : ComponentCodec<C> {
    override val name get() = schema.name

    private val constructor = type.getDeclaredConstructor().apply { isAccessible = true }
    private val fields = editableFields(type).associateBy { it.name }

    override fun read(node: JsonNode): C = read(node) {}

    override fun read(node: JsonNode, problems: (String) -> Unit): C {
        val values = json.decode(schema, node, problems)
        val component = constructor.newInstance()
        for (field in schema.fields) {
            val javaField = fields.getValue(field.name)
            javaField.set(component, javaValue(javaField, field.type, values.getValue(field.name)))
        }
        return component
    }

    override fun write(component: C): JsonNode =
        json.encode(schema, schema.fields.associate { it.name to schemaValue(it.type, fields.getValue(it.name).get(component)) })
}

/**
 * The game components of [registry], read and checked once (see [ComponentSchemaReader]): construction fails with a
 * [ComponentRegistrationException] when one cannot be registered.
 */
class GameComponents(registry: ComponentRegistry = ComponentRegistry { emptyList() }, reader: ComponentSchemaReader = ComponentSchemaReader()) {
    private val classes = registry.components()

    val schemas: List<ComponentSchema> = reader.readAll(classes, BUILT_IN_COMPONENTS)

    @Suppress("UNCHECKED_CAST")
    val codecs: List<ReflectiveCodec<*>> = schemas.zip(classes) { schema, type -> ReflectiveCodec(schema, type as Class<Component>) }
}
